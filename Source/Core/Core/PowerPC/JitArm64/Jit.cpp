// Copyright 2014 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "Core/PowerPC/JitArm64/Jit.h"

#include <cstdio>

#include "Common/Arm64Emitter.h"
#include "Common/CommonTypes.h"
#include "Common/Logging/Log.h"
#include "Common/MathUtil.h"
#include "Common/PerformanceCounter.h"
#include "Common/StringUtil.h"

#include "Core/ConfigManager.h"
#include "Core/Core.h"
#include "Core/CoreTiming.h"
#include "Core/HLE/HLE.h"
#include "Core/HW/GPFifo.h"
#include "Core/HW/Memmap.h"
#include "Core/HW/ProcessorInterface.h"
#include "Core/PatchEngine.h"
#include "Core/PowerPC/JitArm64/JitArm64_RegCache.h"
#include "Core/PowerPC/JitInterface.h"
#include "Core/PowerPC/Profiler.h"

using namespace Arm64Gen;

constexpr size_t CODE_SIZE = 1024 * 1024 * 32;
// We use a bigger farcode size for JitArm64 than Jit64, because JitArm64 always emits farcode
// for the slow path of each loadstore instruction. Jit64 postpones emitting farcode until the
// farcode actually is needed, saving it from having to emit farcode for most instructions.
// TODO: Perhaps implement something similar to Jit64. But using more RAM isn't much of a problem.
constexpr size_t FARCODE_SIZE = 1024 * 1024 * 64;
constexpr size_t FARCODE_SIZE_MMU = 1024 * 1024 * 64;

constexpr size_t STACK_SIZE = 2 * 1024 * 1024;
constexpr size_t SAFE_STACK_SIZE = 512 * 1024;
constexpr size_t GUARD_SIZE = 64 * 1024;  // two guards - bottom (permanent) and middle (see above)
constexpr size_t GUARD_OFFSET = STACK_SIZE - SAFE_STACK_SIZE - GUARD_SIZE;

JitArm64::JitArm64() : m_float_emit(this)
{
}

JitArm64::~JitArm64() = default;

void JitArm64::Init()
{
  InitializeInstructionTables();

  size_t child_code_size = SConfig::GetInstance().bMMU ? FARCODE_SIZE_MMU : FARCODE_SIZE;
  AllocCodeSpace(CODE_SIZE + child_code_size);
  AddChildCodeSpace(&farcode, child_code_size);

  jo.fastmem_arena = SConfig::GetInstance().bFastmem && Memory::InitFastmemArena();
  jo.enableBlocklink = true;
  jo.optimizeGatherPipe = true;
  UpdateMemoryAndExceptionOptions();
  gpr.Init(this);
  fpr.Init(this);
  blocks.Init();

  code_block.m_stats = &js.st;
  code_block.m_gpa = &js.gpa;
  code_block.m_fpa = &js.fpa;
  analyzer.SetOption(PPCAnalyst::PPCAnalyzer::OPTION_CONDITIONAL_CONTINUE);
  analyzer.SetOption(PPCAnalyst::PPCAnalyzer::OPTION_CARRY_MERGE);
  analyzer.SetOption(PPCAnalyst::PPCAnalyzer::OPTION_BRANCH_FOLLOW);

  m_enable_blr_optimization = jo.enableBlocklink && SConfig::GetInstance().bFastmem &&
                              !SConfig::GetInstance().bEnableDebugging;
  m_cleanup_after_stackfault = false;

  AllocStack();
  GenerateAsm();
}

bool JitArm64::HandleFault(uintptr_t access_address, SContext* ctx)
{
  // We can't handle any fault from other threads.
  if (!Core::IsCPUThread())
  {
    ERROR_LOG(DYNA_REC, "Exception handler - Not on CPU thread");
    DoBacktrace(access_address, ctx);
    return false;
  }

  bool success = false;

  // Handle BLR stack faults, may happen in C++ code.
  uintptr_t stack = (uintptr_t)m_stack_base;
  uintptr_t diff = access_address - stack;
  if (diff >= GUARD_OFFSET && diff < GUARD_OFFSET + GUARD_SIZE)
    success = HandleStackFault();

  // If the fault is in JIT code space, look for fastmem areas.
  if (!success && IsInSpace((u8*)ctx->CTX_PC))
    success = HandleFastmemFault(access_address, ctx);

  if (!success)
  {
    ERROR_LOG(DYNA_REC, "Exception handler - Unhandled fault");
    DoBacktrace(access_address, ctx);
  }
  return success;
}

bool JitArm64::HandleStackFault()
{
  if (!m_enable_blr_optimization)
    return false;

  ERROR_LOG(POWERPC, "BLR cache disabled due to excessive BL in the emulated program.");
  m_enable_blr_optimization = false;
#ifndef _WIN32
  Common::UnWriteProtectMemory(m_stack_base + GUARD_OFFSET, GUARD_SIZE);
#endif
  GetBlockCache()->InvalidateICache(0, 0xffffffff, true);
  CoreTiming::ForceExceptionCheck(0);
  m_cleanup_after_stackfault = true;

  return true;
}

void JitArm64::ClearCache()
{
  m_fault_to_handler.clear();

  blocks.Clear();
  ClearCodeSpace();
  farcode.ClearCodeSpace();
  UpdateMemoryAndExceptionOptions();

  GenerateAsm();
}

void JitArm64::Shutdown()
{
  Memory::ShutdownFastmemArena();
  FreeCodeSpace();
  blocks.Shutdown();
  FreeStack();
}

void JitArm64::FallBackToInterpreter(UGeckoInstruction inst)
{
  FlushCarry();
  gpr.Flush(FlushMode::All, ARM64Reg::INVALID_REG);
  fpr.Flush(FlushMode::All, ARM64Reg::INVALID_REG);

  if (js.op->opinfo->flags & FL_ENDBLOCK)
  {
    // also flush the program counter
    ARM64Reg WA = gpr.GetReg();
    MOVI2R(WA, js.compilerPC);
    STR(IndexType::Unsigned, WA, PPC_REG, PPCSTATE_OFF(pc));
    ADD(WA, WA, 4);
    STR(IndexType::Unsigned, WA, PPC_REG, PPCSTATE_OFF(npc));
    gpr.Unlock(WA);
  }

  Interpreter::Instruction instr = PPCTables::GetInterpreterOp(inst);
  MOVP2R(ARM64Reg::X8, instr);
  MOVI2R(ARM64Reg::W0, inst.hex);
  BLR(ARM64Reg::X8);

  // If the instruction wrote to any registers which were marked as discarded,
  // we must mark them as no longer discarded
  gpr.ResetRegisters(js.op->regsOut);
  fpr.ResetRegisters(js.op->GetFregsOut());

  if (js.op->opinfo->flags & FL_ENDBLOCK)
  {
    if (js.isLastInstruction)
    {
      ARM64Reg WA = gpr.GetReg();
      LDR(IndexType::Unsigned, WA, PPC_REG, PPCSTATE_OFF(npc));
      WriteExceptionExit(WA);
      gpr.Unlock(WA);
    }
    else
    {
      // only exit if ppcstate.npc was changed
      ARM64Reg WA = gpr.GetReg();
      LDR(IndexType::Unsigned, WA, PPC_REG, PPCSTATE_OFF(npc));
      ARM64Reg WB = gpr.GetReg();
      MOVI2R(WB, js.compilerPC + 4);
      CMP(WB, WA);
      gpr.Unlock(WB);
      FixupBranch c = B(CC_EQ);
      WriteExceptionExit(WA);
      SetJumpTarget(c);
      gpr.Unlock(WA);
    }
  }
  else if (ShouldHandleFPExceptionForInstruction(js.op))
  {
    WriteConditionalExceptionExit(EXCEPTION_PROGRAM);
  }

  if (jo.memcheck && (js.op->opinfo->flags & FL_LOADSTORE))
  {
    WriteConditionalExceptionExit(EXCEPTION_DSI);
  }
}

void JitArm64::HLEFunction(u32 hook_index)
{
  FlushCarry();
  gpr.Flush(FlushMode::All, ARM64Reg::INVALID_REG);
  fpr.Flush(FlushMode::All, ARM64Reg::INVALID_REG);

  MOVP2R(ARM64Reg::X8, &HLE::Execute);
  MOVI2R(ARM64Reg::W0, js.compilerPC);
  MOVI2R(ARM64Reg::W1, hook_index);
  BLR(ARM64Reg::X8);
}

void JitArm64::DoNothing(UGeckoInstruction inst)
{
  // Yup, just don't do anything.
}

void JitArm64::Break(UGeckoInstruction inst)
{
  WARN_LOG(DYNA_REC, "Breaking! %08x - Fix me ;)", inst.hex);
  exit(0);
}

void JitArm64::Cleanup()
{
  if (jo.optimizeGatherPipe && js.fifoBytesSinceCheck > 0)
  {
    LDP(IndexType::Signed, ARM64Reg::X0, ARM64Reg::X1, PPC_REG, PPCSTATE_OFF(gather_pipe_ptr));
    SUB(ARM64Reg::X0, ARM64Reg::X0, ARM64Reg::X1);
    CMP(ARM64Reg::X0, GPFifo::GATHER_PIPE_SIZE);
    FixupBranch exit = B(CC_LT);
    MOVP2R(ARM64Reg::X0, &GPFifo::UpdateGatherPipe);
    BLR(ARM64Reg::X0);
    SetJumpTarget(exit);
  }

  // SPEED HACK: MMCR0/MMCR1 should be checked at run-time, not at compile time.
  if (MMCR0.Hex || MMCR1.Hex)
  {
    MOVP2R(ARM64Reg::X8, &PowerPC::UpdatePerformanceMonitor);
    MOVI2R(ARM64Reg::X0, js.downcountAmount);
    MOVI2R(ARM64Reg::X1, js.numLoadStoreInst);
    MOVI2R(ARM64Reg::X2, js.numFloatingPointInst);
    BLR(ARM64Reg::X8);
  }
}

void JitArm64::DoDownCount()
{
  LDR(IndexType::Unsigned, ARM64Reg::W0, PPC_REG, PPCSTATE_OFF(downcount));
  SUBSI2R(ARM64Reg::W0, ARM64Reg::W0, js.downcountAmount, ARM64Reg::W1);
  STR(IndexType::Unsigned, ARM64Reg::W0, PPC_REG, PPCSTATE_OFF(downcount));
}

void JitArm64::ResetStack()
{
  if (!m_enable_blr_optimization)
    return;

  LDR(IndexType::Unsigned, ARM64Reg::X0, PPC_REG, PPCSTATE_OFF(stored_stack_pointer));
  ADD(ARM64Reg::SP, ARM64Reg::X0, 0);
}

void JitArm64::AllocStack()
{
  if (!m_enable_blr_optimization)
    return;

#ifndef _WIN32
  m_stack_base = static_cast<u8*>(Common::AllocateMemoryPages(STACK_SIZE));
  if (!m_stack_base)
  {
    m_enable_blr_optimization = false;
    return;
  }

  m_stack_pointer = m_stack_base + STACK_SIZE;
  Common::ReadProtectMemory(m_stack_base, GUARD_SIZE);
  Common::ReadProtectMemory(m_stack_base + GUARD_OFFSET, GUARD_SIZE);
#else
  // For windows we just keep using the system stack and reserve a large amount of memory at the end
  // of the stack.
  ULONG reserveSize = SAFE_STACK_SIZE;
  SetThreadStackGuarantee(&reserveSize);
#endif
}

void JitArm64::FreeStack()
{
#ifndef _WIN32
  if (m_stack_base)
    Common::FreeMemoryPages(m_stack_base, STACK_SIZE);
  m_stack_base = nullptr;
  m_stack_pointer = nullptr;
#endif
}

void JitArm64::WriteExit(u32 destination, bool LK, u32 exit_address_after_return)
{
  Cleanup();
  EndTimeProfile(js.curBlock);
  DoDownCount();

  LK &= m_enable_blr_optimization;

  if (LK)
  {
    // Push {ARM_PC+20; PPC_PC} on the stack
    MOVI2R(ARM64Reg::X1, exit_address_after_return);
    ADR(ARM64Reg::X0, 20);
    STP(IndexType::Pre, ARM64Reg::X0, ARM64Reg::X1, ARM64Reg::SP, -16);
  }

  JitBlock* b = js.curBlock;
  JitBlock::LinkData linkData;
  linkData.exitAddress = destination;
  linkData.exitPtrs = GetWritableCodePtr();
  linkData.linkStatus = false;
  linkData.call = LK;
  b->linkData.push_back(linkData);

  blocks.WriteLinkBlock(*this, linkData);

  if (LK)
  {
    // Write the regular exit node after the return.
    linkData.exitAddress = exit_address_after_return;
    linkData.exitPtrs = GetWritableCodePtr();
    linkData.linkStatus = false;
    linkData.call = false;
    b->linkData.push_back(linkData);

    blocks.WriteLinkBlock(*this, linkData);
  }
}

void JitArm64::WriteExit(Arm64Gen::ARM64Reg dest, bool LK, u32 exit_address_after_return)
{
  if (dest != DISPATCHER_PC)
    MOV(DISPATCHER_PC, dest);

  Cleanup();
  EndTimeProfile(js.curBlock);
  DoDownCount();

  LK &= m_enable_blr_optimization;

  if (!LK)
  {
    B(dispatcher);
  }
  else
  {
    // Push {ARM_PC, PPC_PC} on the stack
    MOVI2R(ARM64Reg::X1, exit_address_after_return);
    ADR(ARM64Reg::X0, 12);
    STP(IndexType::Pre, ARM64Reg::X0, ARM64Reg::X1, ARM64Reg::SP, -16);

    BL(dispatcher);

    // Write the regular exit node after the return.
    JitBlock* b = js.curBlock;
    JitBlock::LinkData linkData;
    linkData.exitAddress = exit_address_after_return;
    linkData.exitPtrs = GetWritableCodePtr();
    linkData.linkStatus = false;
    linkData.call = false;
    b->linkData.push_back(linkData);

    blocks.WriteLinkBlock(*this, linkData);
  }
}

void JitArm64::FakeLKExit(u32 exit_address_after_return)
{
  if (!m_enable_blr_optimization)
    return;

  // We may need to fake the BLR stack on inlined CALL instructions.
  // Else we can't return to this location any more.
  gpr.Lock(ARM64Reg::W30);
  ARM64Reg after_reg = gpr.GetReg();
  ARM64Reg code_reg = gpr.GetReg();
  MOVI2R(after_reg, exit_address_after_return);
  ADR(EncodeRegTo64(code_reg), 12);
  STP(IndexType::Pre, EncodeRegTo64(code_reg), EncodeRegTo64(after_reg), ARM64Reg::SP, -16);
  gpr.Unlock(after_reg, code_reg);

  FixupBranch skip_exit = BL();
  gpr.Unlock(ARM64Reg::W30);

  // Write the regular exit node after the return.
  JitBlock* b = js.curBlock;
  JitBlock::LinkData linkData;
  linkData.exitAddress = exit_address_after_return;
  linkData.exitPtrs = GetWritableCodePtr();
  linkData.linkStatus = false;
  linkData.call = false;
  b->linkData.push_back(linkData);

  blocks.WriteLinkBlock(*this, linkData);

  SetJumpTarget(skip_exit);
}

void JitArm64::WriteBLRExit(Arm64Gen::ARM64Reg dest)
{
  if (!m_enable_blr_optimization)
  {
    WriteExit(dest);
    return;
  }

  if (dest != DISPATCHER_PC)
    MOV(DISPATCHER_PC, dest);

  Cleanup();
  EndTimeProfile(js.curBlock);

  // Check if {ARM_PC, PPC_PC} matches the current state.
  LDP(IndexType::Post, ARM64Reg::X2, ARM64Reg::X1, ARM64Reg::SP, 16);
  CMP(ARM64Reg::W1, DISPATCHER_PC);
  FixupBranch no_match = B(CC_NEQ);

  DoDownCount();  // overwrites X0 + X1

  RET(ARM64Reg::X2);

  SetJumpTarget(no_match);

  ResetStack();

  DoDownCount();

  B(dispatcher);
}

void JitArm64::WriteExceptionExit(u32 destination, bool only_external, bool always_exception)
{
  MOVI2R(DISPATCHER_PC, destination);
  WriteExceptionExit(DISPATCHER_PC, only_external, always_exception);
}

void JitArm64::WriteExceptionExit(ARM64Reg dest, bool only_external, bool always_exception)
{
  if (dest != DISPATCHER_PC)
    MOV(DISPATCHER_PC, dest);

  Cleanup();

  FixupBranch no_exceptions;
  if (!always_exception)
  {
    LDR(IndexType::Unsigned, ARM64Reg::W30, PPC_REG, PPCSTATE_OFF(Exceptions));
    no_exceptions = CBZ(ARM64Reg::W30);
  }

  STR(IndexType::Unsigned, DISPATCHER_PC, PPC_REG, PPCSTATE_OFF(pc));
  STR(IndexType::Unsigned, DISPATCHER_PC, PPC_REG, PPCSTATE_OFF(npc));
  if (only_external)
    MOVP2R(EncodeRegTo64(DISPATCHER_PC), &PowerPC::CheckExternalExceptions);
  else
    MOVP2R(EncodeRegTo64(DISPATCHER_PC), &PowerPC::CheckExceptions);
  BLR(EncodeRegTo64(DISPATCHER_PC));
  LDR(IndexType::Unsigned, DISPATCHER_PC, PPC_REG, PPCSTATE_OFF(npc));

  if (!always_exception)
    SetJumpTarget(no_exceptions);

  EndTimeProfile(js.curBlock);
  DoDownCount();

  B(dispatcher);
}

void JitArm64::WriteConditionalExceptionExit(int exception, u64 increment_sp_on_exit)
{
  ARM64Reg WA = gpr.GetReg();
  WriteConditionalExceptionExit(exception, WA, Arm64Gen::ARM64Reg::INVALID_REG,
                                increment_sp_on_exit);
  gpr.Unlock(WA);
}

void JitArm64::WriteConditionalExceptionExit(int exception, ARM64Reg temp_gpr, ARM64Reg temp_fpr,
                                             u64 increment_sp_on_exit)
{
  LDR(IndexType::Unsigned, temp_gpr, PPC_REG, PPCSTATE_OFF(Exceptions));
  FixupBranch no_exception = TBZ(temp_gpr, IntLog2(exception));

  const bool switch_to_far_code = !IsInFarCode();

  if (switch_to_far_code)
  {
    FixupBranch handle_exception = B();
    SwitchToFarCode();
    SetJumpTarget(handle_exception);
  }

  if (increment_sp_on_exit != 0)
    ADDI2R(ARM64Reg::SP, ARM64Reg::SP, increment_sp_on_exit, temp_gpr);

  gpr.Flush(FlushMode::MaintainState, temp_gpr);
  fpr.Flush(FlushMode::MaintainState, temp_fpr);

  WriteExceptionExit(js.compilerPC, false, true);

  if (switch_to_far_code)
    SwitchToNearCode();

  SetJumpTarget(no_exception);
}

bool JitArm64::HandleFunctionHooking(u32 address)
{
  return HLE::ReplaceFunctionIfPossible(address, [&](u32 hook_index, HLE::HookType type) {
    HLEFunction(hook_index);

    if (type != HLE::HookType::Replace)
      return false;

    LDR(IndexType::Unsigned, DISPATCHER_PC, PPC_REG, PPCSTATE_OFF(npc));
    js.downcountAmount += js.st.numCycles;
    WriteExit(DISPATCHER_PC);
    return true;
  });
}

void JitArm64::DumpCode(const u8* start, const u8* end)
{
  std::string output;
  for (const u8* code = start; code < end; code += sizeof(u32))
    output += StringFromFormat("%08x", Common::swap32(code));
  WARN_LOG(DYNA_REC, "Code dump from %p to %p:\n%s", start, end, output.c_str());
}

void JitArm64::BeginTimeProfile(JitBlock* b)
{
  MOVP2R(ARM64Reg::X0, &b->profile_data);
  LDR(IndexType::Unsigned, ARM64Reg::X1, ARM64Reg::X0, offsetof(JitBlock::ProfileData, runCount));
  ADD(ARM64Reg::X1, ARM64Reg::X1, 1);

  // Fetch the current counter register
  CNTVCT(ARM64Reg::X2);

  // stores runCount and ticStart
  STP(IndexType::Signed, ARM64Reg::X1, ARM64Reg::X2, ARM64Reg::X0, offsetof(JitBlock::ProfileData, runCount));
}

void JitArm64::EndTimeProfile(JitBlock* b)
{
  if (!jo.profile_blocks)
    return;

  // Fetch the current counter register
  CNTVCT(ARM64Reg::X1);

  MOVP2R(ARM64Reg::X0, &b->profile_data);

  LDR(IndexType::Unsigned, ARM64Reg::X2, ARM64Reg::X0, offsetof(JitBlock::ProfileData, ticStart));
  SUB(ARM64Reg::X1, ARM64Reg::X1, ARM64Reg::X2);

  // loads ticCounter and downcountCounter
  LDP(IndexType::Signed, ARM64Reg::X2, ARM64Reg::X3, ARM64Reg::X0,
      offsetof(JitBlock::ProfileData, ticCounter));
  ADD(ARM64Reg::X2, ARM64Reg::X2, ARM64Reg::X1);
  ADDI2R(ARM64Reg::X3, ARM64Reg::X3, js.downcountAmount, ARM64Reg::X1);

  // stores ticCounter and downcountCounter
  STP(IndexType::Signed, ARM64Reg::X2, ARM64Reg::X3, ARM64Reg::X0,
      offsetof(JitBlock::ProfileData, ticCounter));
}

void JitArm64::Run()
{
  CompiledCode pExecAddr = (CompiledCode)enter_code;
  pExecAddr();
}

void JitArm64::SingleStep()
{
  CompiledCode pExecAddr = (CompiledCode)enter_code;
  pExecAddr();
}

void JitArm64::Jit(u32)
{
  if (m_cleanup_after_stackfault)
  {
    ClearCache();
    m_cleanup_after_stackfault = false;
#ifdef _WIN32
    // The stack is in an invalid state with no guard page, reset it.
    _resetstkoflw();
#endif
  }

  if (IsAlmostFull() || farcode.IsAlmostFull() || SConfig::GetInstance().bJITNoBlockCache)
  {
    ClearCache();
  }

  std::size_t block_size = m_code_buffer.size();
  const u32 em_address = PowerPC::ppcState.pc;

  if (SConfig::GetInstance().bEnableDebugging)
  {
    // Comment out the following to disable breakpoints (speed-up)
    block_size = 1;
  }

  // Analyze the block, collect all instructions it is made of (including inlining,
  // if that is enabled), reorder instructions for optimal performance, and join joinable
  // instructions.
  const u32 nextPC = analyzer.Analyze(em_address, &code_block, &m_code_buffer, block_size);

  if (code_block.m_memory_exception)
  {
    // Address of instruction could not be translated
    NPC = nextPC;
    PowerPC::ppcState.Exceptions |= EXCEPTION_ISI;
    PowerPC::CheckExceptions();
    WARN_LOG(POWERPC, "ISI exception at 0x%08x", nextPC);
    return;
  }

  JitBlock* b = blocks.AllocateBlock(em_address);
  DoJit(em_address, b, nextPC);
  blocks.FinalizeBlock(*b, jo.enableBlocklink, code_block.m_physical_addresses);
}

void JitArm64::DoJit(u32 em_address, JitBlock* b, u32 nextPC)
{
  js.isLastInstruction = false;
  js.firstFPInstructionFound = false;
  js.assumeNoPairedQuantize = false;
  js.blockStart = em_address;
  js.fifoBytesSinceCheck = 0;
  js.mustCheckFifo = false;
  js.downcountAmount = 0;
  js.skipInstructions = 0;
  js.curBlock = b;
  js.carryFlagSet = false;
  js.numLoadStoreInst = 0;
  js.numFloatingPointInst = 0;

  u8* const start = GetWritableCodePtr();
  b->checkedEntry = start;

  // Downcount flag check, Only valid for linked blocks
  {
    FixupBranch bail = B(CC_PL);
    MOVI2R(DISPATCHER_PC, js.blockStart);
    B(do_timing);
    SetJumpTarget(bail);
  }

  // Normal entry doesn't need to check for downcount.
  b->normalEntry = GetWritableCodePtr();

  // Conditionally add profiling code.
  if (jo.profile_blocks)
  {
    // get start tic
    BeginTimeProfile(b);
  }

  if (code_block.m_gqr_used.Count() == 1 &&
      js.pairedQuantizeAddresses.find(js.blockStart) == js.pairedQuantizeAddresses.end())
  {
    int gqr = *code_block.m_gqr_used.begin();
    if (!code_block.m_gqr_modified[gqr] && !GQR(gqr))
    {
      LDR(IndexType::Unsigned, ARM64Reg::W0, PPC_REG, PPCSTATE_OFF(spr[SPR_GQR0]) + gqr * 4);
      FixupBranch no_fail = CBZ(ARM64Reg::W0);
      FixupBranch fail = B();
      SwitchToFarCode();
      SetJumpTarget(fail);
      MOVI2R(DISPATCHER_PC, js.blockStart);
      STR(IndexType::Unsigned, DISPATCHER_PC, PPC_REG, PPCSTATE_OFF(pc));
      MOVI2R(ARM64Reg::W0, static_cast<u32>(JitInterface::ExceptionType::PairedQuantize));
      MOVP2R(ARM64Reg::X1, &JitInterface::CompileExceptionCheck);
      BLR(ARM64Reg::X1);
      B(dispatcher_no_check);
      SwitchToNearCode();
      SetJumpTarget(no_fail);
      js.assumeNoPairedQuantize = true;
    }
  }

  gpr.Start(js.gpa);
  fpr.Start(js.fpa);

  // Translate instructions
  for (u32 i = 0; i < code_block.m_num_instructions; i++)
  {
    PPCAnalyst::CodeOp& op = m_code_buffer[i];

    js.compilerPC = op.address;
    js.op = &op;
    js.fpr_is_store_safe = op.fprIsStoreSafeBeforeInst;
    js.instructionNumber = i;
    js.instructionsLeft = (code_block.m_num_instructions - 1) - i;
    const GekkoOPInfo* opinfo = op.opinfo;
    js.downcountAmount += opinfo->numCycles;
    js.isLastInstruction = i == (code_block.m_num_instructions - 1);

    if (!SConfig::GetInstance().bEnableDebugging)
      js.downcountAmount += PatchEngine::GetSpeedhackCycles(js.compilerPC);

    // Skip calling UpdateLastUsed for lmw/stmw - it usually hurts more than it helps
    if (op.inst.OPCD != 46 && op.inst.OPCD != 47)
      gpr.UpdateLastUsed(op.regsIn | op.regsOut);

    BitSet32 fpr_used = op.fregsIn;
    if (op.fregOut >= 0)
      fpr_used[op.fregOut] = true;
    fpr.UpdateLastUsed(fpr_used);

    // Gather pipe writes using a non-immediate address are discovered by profiling.
    bool gatherPipeIntCheck = js.fifoWriteAddresses.find(op.address) != js.fifoWriteAddresses.end();

    if (jo.optimizeGatherPipe && (js.fifoBytesSinceCheck >= 32 || js.mustCheckFifo))
    {
      js.fifoBytesSinceCheck = 0;
      js.mustCheckFifo = false;

      gpr.Lock(ARM64Reg::W30);
      BitSet32 regs_in_use = gpr.GetCallerSavedUsed();
      BitSet32 fprs_in_use = fpr.GetCallerSavedUsed();
      regs_in_use[DecodeReg(ARM64Reg::W30)] = 0;

      ABI_PushRegisters(regs_in_use);
      m_float_emit.ABI_PushRegisters(fprs_in_use, ARM64Reg::X30);
      MOVP2R(ARM64Reg::X8, &GPFifo::FastCheckGatherPipe);
      BLR(ARM64Reg::X8);
      m_float_emit.ABI_PopRegisters(fprs_in_use, ARM64Reg::X30);
      ABI_PopRegisters(regs_in_use);

      // Inline exception check
      LDR(IndexType::Unsigned, ARM64Reg::W30, PPC_REG, PPCSTATE_OFF(Exceptions));
      FixupBranch no_ext_exception = TBZ(ARM64Reg::W30, 3);  // EXCEPTION_EXTERNAL_INT
      FixupBranch exception = B();
      SwitchToFarCode();
      const u8* done_here = GetCodePtr();
      FixupBranch exit = B();
      SetJumpTarget(exception);
      LDR(IndexType::Unsigned, ARM64Reg::W30, PPC_REG, PPCSTATE_OFF(msr));
      TBZ(ARM64Reg::W30, 11, done_here);
      MOVP2R(ARM64Reg::X30, &ProcessorInterface::m_InterruptCause);
      LDR(IndexType::Unsigned, ARM64Reg::W30, ARM64Reg::X30, 0);
      TST(ARM64Reg::W30, 23, 2);
      B(CC_EQ, done_here);

      gpr.Flush(FlushMode::MaintainState, ARM64Reg::W30);
      fpr.Flush(FlushMode::MaintainState, ARM64Reg::INVALID_REG);
      WriteExceptionExit(js.compilerPC, true, true);
      SwitchToNearCode();
      SetJumpTarget(no_ext_exception);
      SetJumpTarget(exit);
      gpr.Unlock(ARM64Reg::W30);

      // So we don't check exceptions twice
      gatherPipeIntCheck = false;
    }
    // Gather pipe writes can generate an exception; add an exception check.
    // TODO: This doesn't really match hardware; the CP interrupt is
    // asynchronous.
    if (jo.optimizeGatherPipe && gatherPipeIntCheck)
    {
      ARM64Reg WA = gpr.GetReg();
      ARM64Reg XA = EncodeRegTo64(WA);

      LDR(IndexType::Unsigned, WA, PPC_REG, PPCSTATE_OFF(Exceptions));
      FixupBranch no_ext_exception = TBZ(WA, 3);  // EXCEPTION_EXTERNAL_INT
      FixupBranch exception = B();
      SwitchToFarCode();
      const u8* done_here = GetCodePtr();
      FixupBranch exit = B();
      SetJumpTarget(exception);
      LDR(IndexType::Unsigned, WA, PPC_REG, PPCSTATE_OFF(msr));
      TBZ(WA, 11, done_here);
      MOVP2R(XA, &ProcessorInterface::m_InterruptCause);
      LDR(IndexType::Unsigned, WA, XA, 0);
      TST(WA, 23, 2);
      B(CC_EQ, done_here);

      gpr.Flush(FlushMode::MaintainState, WA);
      fpr.Flush(FlushMode::MaintainState, ARM64Reg::INVALID_REG);
      WriteExceptionExit(js.compilerPC, true, true);
      SwitchToNearCode();
      SetJumpTarget(no_ext_exception);
      SetJumpTarget(exit);

      gpr.Unlock(WA);
    }

    if (HandleFunctionHooking(op.address))
      break;

    if (!op.skip)
    {
      if ((opinfo->flags & FL_USE_FPU) && !js.firstFPInstructionFound)
      {
        // This instruction uses FPU - needs to add FP exception bailout
        ARM64Reg WA = gpr.GetReg();
        LDR(IndexType::Unsigned, WA, PPC_REG, PPCSTATE_OFF(msr));
        FixupBranch b1 = TBNZ(WA, 13);  // Test FP enabled bit

        FixupBranch far = B();
        SwitchToFarCode();
        SetJumpTarget(far);

        gpr.Flush(FlushMode::MaintainState, WA);
        fpr.Flush(FlushMode::MaintainState, ARM64Reg::INVALID_REG);

        LDR(IndexType::Unsigned, WA, PPC_REG, PPCSTATE_OFF(Exceptions));
        ORR(WA, WA, 26, 0);  // EXCEPTION_FPU_UNAVAILABLE
        STR(IndexType::Unsigned, WA, PPC_REG, PPCSTATE_OFF(Exceptions));

        gpr.Unlock(WA);

        WriteExceptionExit(js.compilerPC, false, true);

        SwitchToNearCode();

        SetJumpTarget(b1);

        js.firstFPInstructionFound = true;
      }

      if (SConfig::GetInstance().bJITRegisterCacheOff)
      {
        gpr.Flush(FlushMode::All, ARM64Reg::INVALID_REG);
        fpr.Flush(FlushMode::All, ARM64Reg::INVALID_REG);
        FlushCarry();
      }

      CompileInstruction(op);

      js.fpr_is_store_safe = op.fprIsStoreSafeAfterInst;

      if (!CanMergeNextInstructions(1) || js.op[1].opinfo->type != ::OpType::Integer)
        FlushCarry();

      // If we have a register that will never be used again, discard or flush it.
      if (!SConfig::GetInstance().bJITRegisterCacheOff)
      {
        gpr.DiscardRegisters(op.gprDiscardable);
        fpr.DiscardRegisters(op.fprDiscardable);
      }
      gpr.StoreRegisters(~op.gprInUse & (op.regsIn | op.regsOut));
      fpr.StoreRegisters(~op.fprInUse & (op.fregsIn | op.GetFregsOut()));

      if (opinfo->flags & FL_LOADSTORE)
        ++js.numLoadStoreInst;

      if (opinfo->flags & FL_USE_FPU)
        ++js.numFloatingPointInst;
    }

    i += js.skipInstructions;
    js.skipInstructions = 0;
  }

  if (code_block.m_broken)
  {
    gpr.Flush(FlushMode::All, ARM64Reg::INVALID_REG);
    fpr.Flush(FlushMode::All, ARM64Reg::INVALID_REG);
    WriteExit(nextPC);
  }

  b->codeSize = (u32)(GetCodePtr() - start);
  b->originalSize = code_block.m_num_instructions;

  FlushIcache();
  farcode.FlushIcache();
}
