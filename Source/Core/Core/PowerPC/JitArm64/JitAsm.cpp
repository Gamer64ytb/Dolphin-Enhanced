// Copyright 2014 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "Common/Arm64Emitter.h"
#include "Common/CommonTypes.h"
#include "Common/FloatUtils.h"
#include "Common/JitRegister.h"
#include "Common/MathUtil.h"

#include "Core/CoreTiming.h"
#include "Core/HW/CPU.h"
#include "Core/HW/Memmap.h"
#include "Core/PowerPC/Gekko.h"
#include "Core/PowerPC/JitArm64/Jit.h"
#include "Core/PowerPC/JitCommon/JitAsmCommon.h"
#include "Core/PowerPC/JitCommon/JitCache.h"
#include "Core/PowerPC/MMU.h"
#include "Core/PowerPC/PowerPC.h"

using namespace Arm64Gen;

void JitArm64::GenerateAsm()
{
  // This value is all of the callee saved registers that we are required to save.
  // According to the AACPS64 we need to save R19 ~ R30 and ARM64Reg::Q8 ~ ARM64Reg::Q15.
  const u32 ALL_CALLEE_SAVED = 0x7FF80000;
  const u32 ALL_CALLEE_SAVED_FPR = 0x0000FF00;
  BitSet32 regs_to_save(ALL_CALLEE_SAVED);
  BitSet32 regs_to_save_fpr(ALL_CALLEE_SAVED_FPR);
  enter_code = GetCodePtr();

  ABI_PushRegisters(regs_to_save);
  m_float_emit.ABI_PushRegisters(regs_to_save_fpr, ARM64Reg::X30);

  MOVP2R(PPC_REG, &PowerPC::ppcState);

  // Swap the stack pointer, so we have proper guard pages.
  ADD(ARM64Reg::X0, ARM64Reg::SP, 0);
  MOVP2R(ARM64Reg::X1, &m_saved_stack_pointer);
  STR(IndexType::Unsigned, ARM64Reg::X0, ARM64Reg::X1, 0);
  MOVP2R(ARM64Reg::X1, &m_stack_pointer);
  LDR(IndexType::Unsigned, ARM64Reg::X0, ARM64Reg::X1, 0);
  FixupBranch no_fake_stack = CBZ(ARM64Reg::X0);
  ADD(ARM64Reg::SP, ARM64Reg::X0, 0);
  SetJumpTarget(no_fake_stack);

  // Push {nullptr; -1} as invalid destination on the stack.
  MOVI2R(ARM64Reg::X0, 0xFFFFFFFF);
  STP(IndexType::Pre, ARM64Reg::ZR, ARM64Reg::X0, ARM64Reg::SP, -16);

  // Store the stack pointer, so we can reset it if the BLR optimization fails.
  ADD(ARM64Reg::X0, ARM64Reg::SP, 0);
  STR(IndexType::Unsigned, ARM64Reg::X0, PPC_REG, PPCSTATE_OFF(stored_stack_pointer));

  // The PC will be loaded into DISPATCHER_PC after the call to CoreTiming::Advance().
  // Advance() does an exception check so we don't know what PC to use until afterwards.
  FixupBranch to_start_of_timing_slice = B();

  // If we align the dispatcher to a page then we can load its location with one ADRP instruction
  // do
  // {
  //   CoreTiming::Advance();  // <-- Checks for exceptions (changes PC)
  //   DISPATCHER_PC = PC;
  //   do
  //   {
  // dispatcher_no_check:
  //     ExecuteBlock(JitBase::Dispatch());
  // dispatcher:
  //   } while (PowerPC::ppcState.downcount > 0);
  // do_timing:
  //   NPC = PC = DISPATCHER_PC;
  // } while (CPU::GetState() == CPU::State::Running);
  AlignCodePage();
  dispatcher = GetCodePtr();
  WARN_LOG(DYNA_REC, "Dispatcher is %p", dispatcher);

  // Downcount Check
  // The result of slice decrementation should be in flags if somebody jumped here
  // IMPORTANT - We jump on negative, not carry!!!
  FixupBranch bail = B(CC_MI);

  dispatcher_no_check = GetCodePtr();

  bool assembly_dispatcher = true;

  if (assembly_dispatcher)
  {
    // set the mem_base based on MSR flags
    LDR(IndexType::Unsigned, ARM64Reg::W28, PPC_REG, PPCSTATE_OFF(msr));
    FixupBranch physmem = TBNZ(ARM64Reg::W28, 31 - 27);
    MOVP2R(MEM_REG, Memory::physical_base);
    FixupBranch membaseend = B();
    SetJumpTarget(physmem);
    MOVP2R(MEM_REG, Memory::logical_base);
    SetJumpTarget(membaseend);

    // iCache[(address >> 2) & iCache_Mask];
    ARM64Reg pc_masked = ARM64Reg::W25;
    ARM64Reg cache_base = ARM64Reg::X27;
    ARM64Reg block = ARM64Reg::X30;
    ORRI2R(pc_masked, ARM64Reg::WZR, JitBaseBlockCache::FAST_BLOCK_MAP_MASK << 3);
    AND(pc_masked, pc_masked, DISPATCHER_PC, ArithOption(DISPATCHER_PC, ShiftType::LSL, 1));
    MOVP2R(cache_base, GetBlockCache()->GetFastBlockMap());
    LDR(block, cache_base, EncodeRegTo64(pc_masked));
    FixupBranch not_found = CBZ(block);

    // b.effectiveAddress != addr || b.msrBits != msr
    ARM64Reg pc_and_msr = ARM64Reg::W25;
    ARM64Reg pc_and_msr2 = ARM64Reg::W24;
    LDR(IndexType::Unsigned, pc_and_msr, block, offsetof(JitBlock, effectiveAddress));
    CMP(pc_and_msr, DISPATCHER_PC);
    FixupBranch pc_missmatch = B(CC_NEQ);

    LDR(IndexType::Unsigned, pc_and_msr2, PPC_REG, PPCSTATE_OFF(msr));
    ANDI2R(pc_and_msr2, pc_and_msr2, JitBaseBlockCache::JIT_CACHE_MSR_MASK);
    LDR(IndexType::Unsigned, pc_and_msr, block, offsetof(JitBlock, msrBits));
    CMP(pc_and_msr, pc_and_msr2);
    FixupBranch msr_missmatch = B(CC_NEQ);

    // return blocks[block_num].normalEntry;
    LDR(IndexType::Unsigned, block, block, offsetof(JitBlock, normalEntry));
    BR(block);
    SetJumpTarget(not_found);
    SetJumpTarget(pc_missmatch);
    SetJumpTarget(msr_missmatch);
  }

  // Call C version of Dispatch().
  STR(IndexType::Unsigned, DISPATCHER_PC, PPC_REG, PPCSTATE_OFF(pc));
  MOVP2R(ARM64Reg::X8, reinterpret_cast<void*>(&JitBase::Dispatch));
  MOVP2R(ARM64Reg::X0, this);
  BLR(ARM64Reg::X8);

  FixupBranch no_block_available = CBZ(ARM64Reg::X0);

  // set the mem_base based on MSR flags and jump to next block.
  LDR(IndexType::Unsigned, ARM64Reg::W28, PPC_REG, PPCSTATE_OFF(msr));
  FixupBranch physmem = TBNZ(ARM64Reg::W28, 31 - 27);
  MOVP2R(MEM_REG, Memory::physical_base);
  BR(ARM64Reg::X0);
  SetJumpTarget(physmem);
  MOVP2R(MEM_REG, Memory::logical_base);
  BR(ARM64Reg::X0);

  // Call JIT
  SetJumpTarget(no_block_available);
  ResetStack();
  MOVP2R(ARM64Reg::X0, this);
  MOV(ARM64Reg::W1, DISPATCHER_PC);
  MOVP2R(ARM64Reg::X8, reinterpret_cast<void*>(&JitTrampoline));
  BLR(ARM64Reg::X8);
  LDR(IndexType::Unsigned, DISPATCHER_PC, PPC_REG, PPCSTATE_OFF(pc));
  B(dispatcher_no_check);

  SetJumpTarget(bail);
  do_timing = GetCodePtr();
  // Write the current PC out to PPCSTATE
  STR(IndexType::Unsigned, DISPATCHER_PC, PPC_REG, PPCSTATE_OFF(pc));
  STR(IndexType::Unsigned, DISPATCHER_PC, PPC_REG, PPCSTATE_OFF(npc));

  // Check the state pointer to see if we are exiting
  // Gets checked on at the end of every slice
  MOVP2R(ARM64Reg::X0, CPU::GetStatePtr());
  LDR(IndexType::Unsigned, ARM64Reg::W0, ARM64Reg::X0, 0);

  CMP(ARM64Reg::W0, 0);
  FixupBranch Exit = B(CC_NEQ);

  SetJumpTarget(to_start_of_timing_slice);
  MOVP2R(ARM64Reg::X8, &CoreTiming::Advance);
  BLR(ARM64Reg::X8);

  // Load the PC back into DISPATCHER_PC (the exception handler might have changed it)
  LDR(IndexType::Unsigned, DISPATCHER_PC, PPC_REG, PPCSTATE_OFF(pc));

  // We can safely assume that downcount >= 1
  B(dispatcher_no_check);

  SetJumpTarget(Exit);

  // Reset the stack pointer, as the BLR optimization have touched it.
  MOVP2R(ARM64Reg::X1, &m_saved_stack_pointer);
  LDR(IndexType::Unsigned, ARM64Reg::X0, ARM64Reg::X1, 0);
  ADD(ARM64Reg::SP, ARM64Reg::X0, 0);

  m_float_emit.ABI_PopRegisters(regs_to_save_fpr, ARM64Reg::X30);
  ABI_PopRegisters(regs_to_save);
  RET(ARM64Reg::X30);

  JitRegister::Register(enter_code, GetCodePtr(), "JIT_Dispatcher");

  GenerateCommonAsm();

  FlushIcache();
}

void JitArm64::GenerateCommonAsm()
{
  GetAsmRoutines()->cdts = GetCodePtr();
  GenerateConvertDoubleToSingle();
  JitRegister::Register(GetAsmRoutines()->cdts, GetCodePtr(), "JIT_cdts");

  GetAsmRoutines()->cstd = GetCodePtr();
  GenerateConvertSingleToDouble();
  JitRegister::Register(GetAsmRoutines()->cdts, GetCodePtr(), "JIT_cstd");

  GetAsmRoutines()->fprf_single = GetCodePtr();
  GenerateFPRF(true);
  GetAsmRoutines()->fprf_double = GetCodePtr();
  GenerateFPRF(false);
  JitRegister::Register(GetAsmRoutines()->fprf_single, GetCodePtr(), "JIT_FPRF");

  GenerateQuantizedLoadStores();
}

// Input in X0, output in W1, clobbers X0-X3 and flags.
void JitArm64::GenerateConvertDoubleToSingle()
{
  UBFX(ARM64Reg::X2, ARM64Reg::X0, 52, 11);
  SUB(ARM64Reg::W3, ARM64Reg::W2, 874);
  CMP(ARM64Reg::W3, 896 - 874);
  LSR(ARM64Reg::X1, ARM64Reg::X0, 32);
  FixupBranch denormal = B(CCFlags::CC_LS);

  ANDI2R(ARM64Reg::X1, ARM64Reg::X1, 0xc0000000);
  BFXIL(ARM64Reg::X1, ARM64Reg::X0, 29, 30);
  RET();

  SetJumpTarget(denormal);
  LSR(ARM64Reg::X3, ARM64Reg::X0, 21);
  MOVZ(ARM64Reg::X0, 905);
  ORRI2R(ARM64Reg::W3, ARM64Reg::W3, 0x80000000);
  SUB(ARM64Reg::W2, ARM64Reg::W0, ARM64Reg::W2);
  LSRV(ARM64Reg::W2, ARM64Reg::W3, ARM64Reg::W2);
  ANDI2R(ARM64Reg::X3, ARM64Reg::X1, 0x80000000);
  ORR(ARM64Reg::X1, ARM64Reg::X3, ARM64Reg::X2);
  RET();
}

// Input in W0, output in X0, clobbers X0-X4 and flags.
void JitArm64::GenerateConvertSingleToDouble()
{
  UBFX(ARM64Reg::W1, ARM64Reg::W0, 23, 8);
  FixupBranch normal_or_nan = CBNZ(ARM64Reg::W1);

  ANDI2R(ARM64Reg::W1, ARM64Reg::W0, 0x007fffff);
  FixupBranch denormal = CBNZ(ARM64Reg::W1);

  // Zero
  LSL(ARM64Reg::X0, ARM64Reg::X0, 32);
  RET();

  SetJumpTarget(denormal);
  ANDI2R(ARM64Reg::W2, ARM64Reg::W0, 0x80000000);
  CLZ(ARM64Reg::X3, ARM64Reg::X1);
  LSL(ARM64Reg::X2, ARM64Reg::X2, 32);
  ORRI2R(ARM64Reg::X4, ARM64Reg::X3, 0xffffffffffffffc0);
  SUB(ARM64Reg::X2, ARM64Reg::X2, ARM64Reg::X3, ArithOption(ARM64Reg::X3, ShiftType::LSL, 52));
  ADD(ARM64Reg::X3, ARM64Reg::X4, 23);
  LSLV(ARM64Reg::X1, ARM64Reg::X1, ARM64Reg::X3);
  BFI(ARM64Reg::X2, ARM64Reg::X1, 30, 22);
  MOVI2R(ARM64Reg::X1, 0x3a90000000000000);
  ADD(ARM64Reg::X0, ARM64Reg::X2, ARM64Reg::X1);
  RET();

  SetJumpTarget(normal_or_nan);
  CMP(ARM64Reg::W1, 0xff);
  ANDI2R(ARM64Reg::W2, ARM64Reg::W0, 0x40000000);
  CSET(ARM64Reg::W4, CCFlags::CC_NEQ);
  ANDI2R(ARM64Reg::W3, ARM64Reg::W0, 0xc0000000);
  EOR(ARM64Reg::W2, ARM64Reg::W4, ARM64Reg::W2, ArithOption(ARM64Reg::W2, ShiftType::LSR, 30));
  MOVI2R(ARM64Reg::X1, 0x3800000000000000);
  ANDI2R(ARM64Reg::W4, ARM64Reg::W0, 0x3fffffff);
  LSL(ARM64Reg::X3, ARM64Reg::X3, 32);
  CMP(ARM64Reg::W2, 0);
  CSEL(ARM64Reg::X1, ARM64Reg::X1, ARM64Reg::ZR, CCFlags::CC_NEQ);
  BFI(ARM64Reg::X3, ARM64Reg::X4, 29, 30);
  ORR(ARM64Reg::X0, ARM64Reg::X3, ARM64Reg::X1);
  RET();
}

// Input in X0. Outputs to memory (PPCState). Clobbers X0-X4 and flags.
void JitArm64::GenerateFPRF(bool single)
{
  const auto reg_encoder = single ? EncodeRegTo32 : EncodeRegTo64;

  const ARM64Reg input_reg = reg_encoder(ARM64Reg::W0);
  const ARM64Reg temp_reg = reg_encoder(ARM64Reg::W1);
  const ARM64Reg exp_reg = reg_encoder(ARM64Reg::W2);

  constexpr ARM64Reg fprf_reg = ARM64Reg::W3;
  constexpr ARM64Reg fpscr_reg = ARM64Reg::W4;

  const auto INPUT_EXP_MASK = single ? Common::FLOAT_EXP : Common::DOUBLE_EXP;
  const auto INPUT_FRAC_MASK = single ? Common::FLOAT_FRAC : Common::DOUBLE_FRAC;
  constexpr u32 OUTPUT_SIGN_MASK = 0xC;

  // This code is duplicated for the most common cases for performance.
  // For the less common cases, we branch to an existing copy of this code.
  auto emit_write_fprf_and_ret = [&] {
    BFI(fpscr_reg, fprf_reg, FPRF_SHIFT, FPRF_WIDTH);
    STR(IndexType::Unsigned, fpscr_reg, PPC_REG, PPCSTATE_OFF(fpscr));
    RET();
  };

  // First of all, start the load of the old FPSCR value, in case it takes a while
  LDR(IndexType::Unsigned, fpscr_reg, PPC_REG, PPCSTATE_OFF(fpscr));

  CMP(input_reg, 0);  // Grab sign bit (conveniently the same bit for floats as for integers)
  ANDI2R(exp_reg, input_reg, INPUT_EXP_MASK);  // Grab exponent

  // Most branches handle the sign in the same way. Perform that handling before branching
  MOVI2R(ARM64Reg::W3, Common::PPC_FPCLASS_PN);
  MOVI2R(ARM64Reg::W1, Common::PPC_FPCLASS_NN);
  CSEL(fprf_reg, ARM64Reg::W1, ARM64Reg::W3, CCFlags::CC_LT);

  FixupBranch zero_or_denormal = CBZ(exp_reg);

  // exp != 0
  MOVI2R(temp_reg, INPUT_EXP_MASK);
  CMP(exp_reg, temp_reg);
  FixupBranch nan_or_inf = B(CCFlags::CC_EQ);

  // exp != 0 && exp != EXP_MASK
  const u8* normal = GetCodePtr();
  emit_write_fprf_and_ret();

  // exp == 0
  SetJumpTarget(zero_or_denormal);
  TSTI2R(input_reg, INPUT_FRAC_MASK);
  FixupBranch denormal;
  if (single)
  {
    // To match the interpreter, what we output should be based on how the input would be classified
    // after conversion to double. Converting a denormal single to a double always results in a
    // normal double, so for denormal singles we need to output PPC_FPCLASS_PN/PPC_FPCLASS_NN.
    // TODO: Hardware test that the interpreter actually is correct.
    B(CCFlags::CC_NEQ, normal);
  }
  else
  {
    denormal = B(CCFlags::CC_NEQ);
  }

  // exp == 0 && frac == 0
  LSR(ARM64Reg::W1, fprf_reg, 3);
  MOVI2R(fprf_reg, Common::PPC_FPCLASS_PZ & ~OUTPUT_SIGN_MASK);
  BFI(fprf_reg, ARM64Reg::W1, 4, 1);
  const u8* write_fprf_and_ret = GetCodePtr();
  emit_write_fprf_and_ret();

  // exp == 0 && frac != 0
  if (!single)
    SetJumpTarget(denormal);
  ORRI2R(fprf_reg, fprf_reg, Common::PPC_FPCLASS_PD & ~OUTPUT_SIGN_MASK);
  B(write_fprf_and_ret);

  // exp == EXP_MASK
  SetJumpTarget(nan_or_inf);
  TSTI2R(input_reg, INPUT_FRAC_MASK);
  ORRI2R(ARM64Reg::W1, fprf_reg, Common::PPC_FPCLASS_PINF & ~OUTPUT_SIGN_MASK);
  MOVI2R(ARM64Reg::W2, Common::PPC_FPCLASS_QNAN);
  CSEL(fprf_reg, ARM64Reg::W1, ARM64Reg::W2, CCFlags::CC_EQ);
  B(write_fprf_and_ret);
}

void JitArm64::GenerateQuantizedLoadStores()
{
  // ARM64Reg::X0 is the scale
  // ARM64Reg::X1 is address
  // ARM64Reg::X2 is a temporary on stores
  // ARM64Reg::X30 is LR
  // ARM64Reg::Q0 is the return for loads
  //    is the register for stores
  // ARM64Reg::Q1 is a temporary
  ARM64Reg addr_reg = ARM64Reg::X1;
  ARM64Reg scale_reg = ARM64Reg::X0;
  ARM64FloatEmitter float_emit(this);

  const u8* start = GetCodePtr();
  const u8* loadPairedIllegal = GetCodePtr();
  BRK(100);
  const u8* loadPairedFloatTwo = GetCodePtr();
  {
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.LD1(32, 1, ARM64Reg::D0, addr_reg);
    float_emit.REV32(8, ARM64Reg::D0, ARM64Reg::D0);
    RET(ARM64Reg::X30);
  }
  const u8* loadPairedU8Two = GetCodePtr();
  {
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.LDR(16, IndexType::Unsigned, ARM64Reg::D0, addr_reg, 0);
    float_emit.UXTL(8, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.UXTL(16, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.UCVTF(32, ARM64Reg::D0, ARM64Reg::D0);

    MOVP2R(addr_reg, &m_dequantizeTableS);
    ADD(scale_reg, addr_reg, scale_reg, ArithOption(scale_reg, ShiftType::LSL, 3));
    float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D1, scale_reg, 0);
    float_emit.FMUL(32, ARM64Reg::D0, ARM64Reg::D0, ARM64Reg::D1, 0);
    RET(ARM64Reg::X30);
  }
  const u8* loadPairedS8Two = GetCodePtr();
  {
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.LDR(16, IndexType::Unsigned, ARM64Reg::D0, addr_reg, 0);
    float_emit.SXTL(8, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.SXTL(16, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.SCVTF(32, ARM64Reg::D0, ARM64Reg::D0);

    MOVP2R(addr_reg, &m_dequantizeTableS);
    ADD(scale_reg, addr_reg, scale_reg, ArithOption(scale_reg, ShiftType::LSL, 3));
    float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D1, scale_reg, 0);
    float_emit.FMUL(32, ARM64Reg::D0, ARM64Reg::D0, ARM64Reg::D1, 0);
    RET(ARM64Reg::X30);
  }
  const u8* loadPairedU16Two = GetCodePtr();
  {
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.LD1(16, 1, ARM64Reg::D0, addr_reg);
    float_emit.REV16(8, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.UXTL(16, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.UCVTF(32, ARM64Reg::D0, ARM64Reg::D0);

    MOVP2R(addr_reg, &m_dequantizeTableS);
    ADD(scale_reg, addr_reg, scale_reg, ArithOption(scale_reg, ShiftType::LSL, 3));
    float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D1, scale_reg, 0);
    float_emit.FMUL(32, ARM64Reg::D0, ARM64Reg::D0, ARM64Reg::D1, 0);
    RET(ARM64Reg::X30);
  }
  const u8* loadPairedS16Two = GetCodePtr();
  {
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.LD1(16, 1, ARM64Reg::D0, addr_reg);
    float_emit.REV16(8, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.SXTL(16, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.SCVTF(32, ARM64Reg::D0, ARM64Reg::D0);

    MOVP2R(addr_reg, &m_dequantizeTableS);
    ADD(scale_reg, addr_reg, scale_reg, ArithOption(scale_reg, ShiftType::LSL, 3));
    float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D1, scale_reg, 0);
    float_emit.FMUL(32, ARM64Reg::D0, ARM64Reg::D0, ARM64Reg::D1, 0);
    RET(ARM64Reg::X30);
  }

  const u8* loadPairedFloatOne = GetCodePtr();
  {
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D0, addr_reg, 0);
    float_emit.REV32(8, ARM64Reg::D0, ARM64Reg::D0);
    RET(ARM64Reg::X30);
  }
  const u8* loadPairedU8One = GetCodePtr();
  {
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.LDR(8, IndexType::Unsigned, ARM64Reg::D0, addr_reg, 0);
    float_emit.UXTL(8, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.UXTL(16, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.UCVTF(32, ARM64Reg::D0, ARM64Reg::D0);

    MOVP2R(addr_reg, &m_dequantizeTableS);
    ADD(scale_reg, addr_reg, scale_reg, ArithOption(scale_reg, ShiftType::LSL, 3));
    float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D1, scale_reg, 0);
    float_emit.FMUL(32, ARM64Reg::D0, ARM64Reg::D0, ARM64Reg::D1, 0);
    RET(ARM64Reg::X30);
  }
  const u8* loadPairedS8One = GetCodePtr();
  {
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.LDR(8, IndexType::Unsigned, ARM64Reg::D0, addr_reg, 0);
    float_emit.SXTL(8, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.SXTL(16, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.SCVTF(32, ARM64Reg::D0, ARM64Reg::D0);

    MOVP2R(addr_reg, &m_dequantizeTableS);
    ADD(scale_reg, addr_reg, scale_reg, ArithOption(scale_reg, ShiftType::LSL, 3));
    float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D1, scale_reg, 0);
    float_emit.FMUL(32, ARM64Reg::D0, ARM64Reg::D0, ARM64Reg::D1, 0);
    RET(ARM64Reg::X30);
  }
  const u8* loadPairedU16One = GetCodePtr();
  {
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.LDR(16, IndexType::Unsigned, ARM64Reg::D0, addr_reg, 0);
    float_emit.REV16(8, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.UXTL(16, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.UCVTF(32, ARM64Reg::D0, ARM64Reg::D0);

    MOVP2R(addr_reg, &m_dequantizeTableS);
    ADD(scale_reg, addr_reg, scale_reg, ArithOption(scale_reg, ShiftType::LSL, 3));
    float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D1, scale_reg, 0);
    float_emit.FMUL(32, ARM64Reg::D0, ARM64Reg::D0, ARM64Reg::D1, 0);
    RET(ARM64Reg::X30);
  }
  const u8* loadPairedS16One = GetCodePtr();
  {
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.LDR(16, IndexType::Unsigned, ARM64Reg::D0, addr_reg, 0);
    float_emit.REV16(8, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.SXTL(16, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.SCVTF(32, ARM64Reg::D0, ARM64Reg::D0);

    MOVP2R(addr_reg, &m_dequantizeTableS);
    ADD(scale_reg, addr_reg, scale_reg, ArithOption(scale_reg, ShiftType::LSL, 3));
    float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D1, scale_reg, 0);
    float_emit.FMUL(32, ARM64Reg::D0, ARM64Reg::D0, ARM64Reg::D1, 0);
    RET(ARM64Reg::X30);
  }

  JitRegister::Register(start, GetCodePtr(), "JIT_QuantizedLoad");

  paired_load_quantized = reinterpret_cast<const u8**>(AlignCode16());
  ReserveCodeSpace(8 * sizeof(u8*));

  paired_load_quantized[0] = loadPairedFloatTwo;
  paired_load_quantized[1] = loadPairedIllegal;
  paired_load_quantized[2] = loadPairedIllegal;
  paired_load_quantized[3] = loadPairedIllegal;
  paired_load_quantized[4] = loadPairedU8Two;
  paired_load_quantized[5] = loadPairedU16Two;
  paired_load_quantized[6] = loadPairedS8Two;
  paired_load_quantized[7] = loadPairedS16Two;

  single_load_quantized = reinterpret_cast<const u8**>(AlignCode16());
  ReserveCodeSpace(8 * sizeof(u8*));

  single_load_quantized[0] = loadPairedFloatOne;
  single_load_quantized[1] = loadPairedIllegal;
  single_load_quantized[2] = loadPairedIllegal;
  single_load_quantized[3] = loadPairedIllegal;
  single_load_quantized[4] = loadPairedU8One;
  single_load_quantized[5] = loadPairedU16One;
  single_load_quantized[6] = loadPairedS8One;
  single_load_quantized[7] = loadPairedS16One;

  // Stores
  start = GetCodePtr();
  const u8* storePairedIllegal = GetCodePtr();
  BRK(0x101);
  const u8* storePairedFloat;
  const u8* storePairedFloatSlow;
  {
    storePairedFloat = GetCodePtr();
    float_emit.REV32(8, ARM64Reg::D0, ARM64Reg::D0);
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.ST1(64, ARM64Reg::Q0, 0, addr_reg, ARM64Reg::SP);
    RET(ARM64Reg::X30);

    storePairedFloatSlow = GetCodePtr();
    float_emit.UMOV(64, ARM64Reg::X0, ARM64Reg::Q0, 0);
    ROR(ARM64Reg::X0, ARM64Reg::X0, 32);
    MOVP2R(ARM64Reg::X2, &PowerPC::Write_U64);
    BR(ARM64Reg::X2);
  }

  const u8* storePairedU8;
  const u8* storePairedU8Slow;
  {
    auto emit_quantize = [this, &float_emit, scale_reg]() {
      MOVP2R(ARM64Reg::X2, &m_quantizeTableS);
      ADD(scale_reg, ARM64Reg::X2, scale_reg, ArithOption(scale_reg, ShiftType::LSL, 3));
      float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D1, scale_reg, 0);
      float_emit.FMUL(32, ARM64Reg::D0, ARM64Reg::D0, ARM64Reg::D1, 0);

      float_emit.FCVTZU(32, ARM64Reg::D0, ARM64Reg::D0);
      float_emit.UQXTN(16, ARM64Reg::D0, ARM64Reg::D0);
      float_emit.UQXTN(8, ARM64Reg::D0, ARM64Reg::D0);
    };

    storePairedU8 = GetCodePtr();
    emit_quantize();
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.ST1(16, ARM64Reg::Q0, 0, addr_reg, ARM64Reg::SP);
    RET(ARM64Reg::X30);

    storePairedU8Slow = GetCodePtr();
    emit_quantize();
    float_emit.UMOV(16, ARM64Reg::W0, ARM64Reg::Q0, 0);
    REV16(ARM64Reg::W0, ARM64Reg::W0);
    MOVP2R(ARM64Reg::X2, &PowerPC::Write_U16);
    BR(ARM64Reg::X2);
  }
  const u8* storePairedS8;
  const u8* storePairedS8Slow;
  {
    auto emit_quantize = [this, &float_emit, scale_reg]() {
      MOVP2R(ARM64Reg::X2, &m_quantizeTableS);
      ADD(scale_reg, ARM64Reg::X2, scale_reg, ArithOption(scale_reg, ShiftType::LSL, 3));
      float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D1, scale_reg, 0);
      float_emit.FMUL(32, ARM64Reg::D0, ARM64Reg::D0, ARM64Reg::D1, 0);

      float_emit.FCVTZS(32, ARM64Reg::D0, ARM64Reg::D0);
      float_emit.SQXTN(16, ARM64Reg::D0, ARM64Reg::D0);
      float_emit.SQXTN(8, ARM64Reg::D0, ARM64Reg::D0);
    };

    storePairedS8 = GetCodePtr();
    emit_quantize();
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.ST1(16, ARM64Reg::Q0, 0, addr_reg, ARM64Reg::SP);
    RET(ARM64Reg::X30);

    storePairedS8Slow = GetCodePtr();
    emit_quantize();
    float_emit.UMOV(16, ARM64Reg::W0, ARM64Reg::Q0, 0);
    REV16(ARM64Reg::W0, ARM64Reg::W0);
    MOVP2R(ARM64Reg::X2, &PowerPC::Write_U16);
    BR(ARM64Reg::X2);
  }

  const u8* storePairedU16;
  const u8* storePairedU16Slow;
  {
    auto emit_quantize = [this, &float_emit, scale_reg]() {
      MOVP2R(ARM64Reg::X2, &m_quantizeTableS);
      ADD(scale_reg, ARM64Reg::X2, scale_reg, ArithOption(scale_reg, ShiftType::LSL, 3));
      float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D1, scale_reg, 0);
      float_emit.FMUL(32, ARM64Reg::D0, ARM64Reg::D0, ARM64Reg::D1, 0);

      float_emit.FCVTZU(32, ARM64Reg::D0, ARM64Reg::D0);
      float_emit.UQXTN(16, ARM64Reg::D0, ARM64Reg::D0);
      float_emit.REV16(8, ARM64Reg::D0, ARM64Reg::D0);
    };

    storePairedU16 = GetCodePtr();
    emit_quantize();
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.ST1(32, ARM64Reg::Q0, 0, addr_reg, ARM64Reg::SP);
    RET(ARM64Reg::X30);

    storePairedU16Slow = GetCodePtr();
    emit_quantize();
    float_emit.REV32(8, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.UMOV(32, ARM64Reg::W0, ARM64Reg::Q0, 0);
    MOVP2R(ARM64Reg::X2, &PowerPC::Write_U32);
    BR(ARM64Reg::X2);
  }
  const u8* storePairedS16;  // Used by Viewtiful Joe's intro movie
  const u8* storePairedS16Slow;
  {
    auto emit_quantize = [this, &float_emit, scale_reg]() {
      MOVP2R(ARM64Reg::X2, &m_quantizeTableS);
      ADD(scale_reg, ARM64Reg::X2, scale_reg, ArithOption(scale_reg, ShiftType::LSL, 3));
      float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D1, scale_reg, 0);
      float_emit.FMUL(32, ARM64Reg::D0, ARM64Reg::D0, ARM64Reg::D1, 0);

      float_emit.FCVTZS(32, ARM64Reg::D0, ARM64Reg::D0);
      float_emit.SQXTN(16, ARM64Reg::D0, ARM64Reg::D0);
      float_emit.REV16(8, ARM64Reg::D0, ARM64Reg::D0);
    };

    storePairedS16 = GetCodePtr();
    emit_quantize();
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.ST1(32, ARM64Reg::Q0, 0, addr_reg, ARM64Reg::SP);
    RET(ARM64Reg::X30);

    storePairedS16Slow = GetCodePtr();
    emit_quantize();
    float_emit.REV32(8, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.UMOV(32, ARM64Reg::W0, ARM64Reg::Q0, 0);
    MOVP2R(ARM64Reg::X2, &PowerPC::Write_U32);
    BR(ARM64Reg::X2);
  }

  const u8* storeSingleFloat;
  const u8* storeSingleFloatSlow;
  {
    storeSingleFloat = GetCodePtr();
    float_emit.REV32(8, ARM64Reg::D0, ARM64Reg::D0);
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.STR(32, IndexType::Unsigned, ARM64Reg::D0, addr_reg, 0);
    RET(ARM64Reg::X30);

    storeSingleFloatSlow = GetCodePtr();
    float_emit.UMOV(32, ARM64Reg::W0, ARM64Reg::Q0, 0);
    MOVP2R(ARM64Reg::X2, &PowerPC::Write_U32);
    BR(ARM64Reg::X2);
  }
  const u8* storeSingleU8;  // Used by MKWii
  const u8* storeSingleU8Slow;
  {
    auto emit_quantize = [this, &float_emit, scale_reg]() {
      MOVP2R(ARM64Reg::X2, &m_quantizeTableS);
      ADD(scale_reg, ARM64Reg::X2, scale_reg, ArithOption(scale_reg, ShiftType::LSL, 3));
      float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D1, scale_reg, 0);
      float_emit.FMUL(32, ARM64Reg::D0, ARM64Reg::D0, ARM64Reg::D1);

      float_emit.FCVTZU(32, ARM64Reg::D0, ARM64Reg::D0);
      float_emit.UQXTN(16, ARM64Reg::D0, ARM64Reg::D0);
      float_emit.UQXTN(8, ARM64Reg::D0, ARM64Reg::D0);
    };

    storeSingleU8 = GetCodePtr();
    emit_quantize();
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.ST1(8, ARM64Reg::Q0, 0, addr_reg);
    RET(ARM64Reg::X30);

    storeSingleU8Slow = GetCodePtr();
    emit_quantize();
    float_emit.UMOV(8, ARM64Reg::W0, ARM64Reg::Q0, 0);
    MOVP2R(ARM64Reg::X2, &PowerPC::Write_U8);
    BR(ARM64Reg::X2);
  }
  const u8* storeSingleS8;
  const u8* storeSingleS8Slow;
  {
    auto emit_quantize = [this, &float_emit, scale_reg]() {
      MOVP2R(ARM64Reg::X2, &m_quantizeTableS);
      ADD(scale_reg, ARM64Reg::X2, scale_reg, ArithOption(scale_reg, ShiftType::LSL, 3));
      float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D1, scale_reg, 0);
      float_emit.FMUL(32, ARM64Reg::D0, ARM64Reg::D0, ARM64Reg::D1);

      float_emit.FCVTZS(32, ARM64Reg::D0, ARM64Reg::D0);
      float_emit.SQXTN(16, ARM64Reg::D0, ARM64Reg::D0);
      float_emit.SQXTN(8, ARM64Reg::D0, ARM64Reg::D0);
    };

    storeSingleS8 = GetCodePtr();
    emit_quantize();
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.ST1(8, ARM64Reg::Q0, 0, addr_reg);
    RET(ARM64Reg::X30);

    storeSingleS8Slow = GetCodePtr();
    emit_quantize();
    float_emit.SMOV(8, ARM64Reg::W0, ARM64Reg::Q0, 0);
    MOVP2R(ARM64Reg::X2, &PowerPC::Write_U8);
    BR(ARM64Reg::X2);
  }
  const u8* storeSingleU16;  // Used by MKWii
  const u8* storeSingleU16Slow;
  {
    auto emit_quantize = [this, &float_emit, scale_reg]() {
      MOVP2R(ARM64Reg::X2, &m_quantizeTableS);
      ADD(scale_reg, ARM64Reg::X2, scale_reg, ArithOption(scale_reg, ShiftType::LSL, 3));
      float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D1, scale_reg, 0);
      float_emit.FMUL(32, ARM64Reg::D0, ARM64Reg::D0, ARM64Reg::D1);

      float_emit.FCVTZU(32, ARM64Reg::D0, ARM64Reg::D0);
      float_emit.UQXTN(16, ARM64Reg::D0, ARM64Reg::D0);
    };

    storeSingleU16 = GetCodePtr();
    emit_quantize();
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.REV16(8, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.ST1(16, ARM64Reg::Q0, 0, addr_reg);
    RET(ARM64Reg::X30);

    storeSingleU16Slow = GetCodePtr();
    emit_quantize();
    float_emit.UMOV(16, ARM64Reg::W0, ARM64Reg::Q0, 0);
    MOVP2R(ARM64Reg::X2, &PowerPC::Write_U16);
    BR(ARM64Reg::X2);
  }
  const u8* storeSingleS16;
  const u8* storeSingleS16Slow;
  {
    auto emit_quantize = [this, &float_emit, scale_reg]() {
      MOVP2R(ARM64Reg::X2, &m_quantizeTableS);
      ADD(scale_reg, ARM64Reg::X2, scale_reg, ArithOption(scale_reg, ShiftType::LSL, 3));
      float_emit.LDR(32, IndexType::Unsigned, ARM64Reg::D1, scale_reg, 0);
      float_emit.FMUL(32, ARM64Reg::D0, ARM64Reg::D0, ARM64Reg::D1);

      float_emit.FCVTZS(32, ARM64Reg::D0, ARM64Reg::D0);
      float_emit.SQXTN(16, ARM64Reg::D0, ARM64Reg::D0);
    };

    storeSingleS16 = GetCodePtr();
    emit_quantize();
    ADD(addr_reg, addr_reg, MEM_REG);
    float_emit.REV16(8, ARM64Reg::D0, ARM64Reg::D0);
    float_emit.ST1(16, ARM64Reg::Q0, 0, addr_reg);
    RET(ARM64Reg::X30);

    storeSingleS16Slow = GetCodePtr();
    emit_quantize();
    float_emit.SMOV(16, ARM64Reg::W0, ARM64Reg::Q0, 0);
    MOVP2R(ARM64Reg::X2, &PowerPC::Write_U16);
    BR(ARM64Reg::X2);
  }

  JitRegister::Register(start, GetCodePtr(), "JIT_QuantizedStore");

  paired_store_quantized = reinterpret_cast<const u8**>(AlignCode16());
  ReserveCodeSpace(32 * sizeof(u8*));

  // Fast
  paired_store_quantized[0] = storePairedFloat;
  paired_store_quantized[1] = storePairedIllegal;
  paired_store_quantized[2] = storePairedIllegal;
  paired_store_quantized[3] = storePairedIllegal;
  paired_store_quantized[4] = storePairedU8;
  paired_store_quantized[5] = storePairedU16;
  paired_store_quantized[6] = storePairedS8;
  paired_store_quantized[7] = storePairedS16;

  paired_store_quantized[8] = storeSingleFloat;
  paired_store_quantized[9] = storePairedIllegal;
  paired_store_quantized[10] = storePairedIllegal;
  paired_store_quantized[11] = storePairedIllegal;
  paired_store_quantized[12] = storeSingleU8;
  paired_store_quantized[13] = storeSingleU16;
  paired_store_quantized[14] = storeSingleS8;
  paired_store_quantized[15] = storeSingleS16;

  // Slow
  paired_store_quantized[16] = storePairedFloatSlow;
  paired_store_quantized[17] = storePairedIllegal;
  paired_store_quantized[18] = storePairedIllegal;
  paired_store_quantized[19] = storePairedIllegal;
  paired_store_quantized[20] = storePairedU8Slow;
  paired_store_quantized[21] = storePairedU16Slow;
  paired_store_quantized[22] = storePairedS8Slow;
  paired_store_quantized[23] = storePairedS16Slow;

  paired_store_quantized[24] = storeSingleFloatSlow;
  paired_store_quantized[25] = storePairedIllegal;
  paired_store_quantized[26] = storePairedIllegal;
  paired_store_quantized[27] = storePairedIllegal;
  paired_store_quantized[28] = storeSingleU8Slow;
  paired_store_quantized[29] = storeSingleU16Slow;
  paired_store_quantized[30] = storeSingleS8Slow;
  paired_store_quantized[31] = storeSingleS16Slow;
}
