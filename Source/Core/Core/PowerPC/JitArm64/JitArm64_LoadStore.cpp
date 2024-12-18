// Copyright 2014 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "Common/Arm64Emitter.h"
#include "Common/BitSet.h"
#include "Common/CommonTypes.h"

#include "Core/ConfigManager.h"
#include "Core/Core.h"
#include "Core/CoreTiming.h"
#include "Core/HW/DSP.h"
#include "Core/HW/MMIO.h"
#include "Core/HW/Memmap.h"
#include "Core/PowerPC/JitArm64/Jit.h"
#include "Core/PowerPC/JitArm64/JitArm64_RegCache.h"
#include "Core/PowerPC/JitArm64/Jit_Util.h"
#include "Core/PowerPC/JitInterface.h"
#include "Core/PowerPC/MMU.h"
#include "Core/PowerPC/PPCTables.h"
#include "Core/PowerPC/PowerPC.h"

using namespace Arm64Gen;

void JitArm64::SafeLoadToReg(u32 dest, s32 addr, s32 offsetReg, u32 flags, s32 offset, bool update)
{
  // We want to make sure to not get LR as a temp register
  gpr.Lock(ARM64Reg::W0, ARM64Reg::W30);

  gpr.BindToRegister(dest, dest == (u32)addr || dest == (u32)offsetReg, false);
  ARM64Reg dest_reg = gpr.R(dest);
  ARM64Reg up_reg = ARM64Reg::INVALID_REG;
  ARM64Reg off_reg = ARM64Reg::INVALID_REG;

  if (addr != -1 && !gpr.IsImm(addr))
    up_reg = gpr.R(addr);

  if (offsetReg != -1 && !gpr.IsImm(offsetReg))
    off_reg = gpr.R(offsetReg);

  ARM64Reg addr_reg = ARM64Reg::W0;
  u32 imm_addr = 0;
  bool is_immediate = false;

  if (offsetReg == -1)
  {
    if (addr != -1)
    {
      if (gpr.IsImm(addr))
      {
        is_immediate = true;
        imm_addr = gpr.GetImm(addr) + offset;
      }
      else
      {
        ADDI2R(addr_reg, up_reg, offset, addr_reg);
      }
    }
    else
    {
      is_immediate = true;
      imm_addr = offset;
    }
  }
  else
  {
    if (addr != -1)
    {
      if (gpr.IsImm(addr) && gpr.IsImm(offsetReg))
      {
        is_immediate = true;
        imm_addr = gpr.GetImm(addr) + gpr.GetImm(offsetReg);
      }
      else if (gpr.IsImm(addr) && !gpr.IsImm(offsetReg))
      {
        u32 reg_offset = gpr.GetImm(addr);
        ADDI2R(addr_reg, off_reg, reg_offset, addr_reg);
      }
      else if (!gpr.IsImm(addr) && gpr.IsImm(offsetReg))
      {
        u32 reg_offset = gpr.GetImm(offsetReg);
        ADDI2R(addr_reg, up_reg, reg_offset, addr_reg);
      }
      else
      {
        ADD(addr_reg, up_reg, off_reg);
      }
    }
    else
    {
      if (gpr.IsImm(offsetReg))
      {
        is_immediate = true;
        imm_addr = gpr.GetImm(offsetReg);
      }
      else
      {
        MOV(addr_reg, off_reg);
      }
    }
  }

  ARM64Reg XA = EncodeRegTo64(addr_reg);

  bool addr_reg_set = !is_immediate;
  const auto set_addr_reg_if_needed = [&] {
      if (!addr_reg_set)
        MOVI2R(XA, imm_addr);
  };

  const bool early_update = !jo.memcheck && dest != static_cast<u32>(addr);
  if (update && early_update)
  {
    gpr.BindToRegister(addr, false);
    set_addr_reg_if_needed();
    MOV(gpr.R(addr), addr_reg);
  }

  BitSet32 regs_in_use = gpr.GetCallerSavedUsed();
  BitSet32 fprs_in_use = fpr.GetCallerSavedUsed();
  if (!update || early_update)
    regs_in_use[DecodeReg(ARM64Reg::W0)] = 0;
  if (!jo.memcheck)
    regs_in_use[DecodeReg(dest_reg)] = 0;

  u32 access_size = BackPatchInfo::GetFlagSize(flags);
  u32 mmio_address = 0;
  if (is_immediate)
    mmio_address = PowerPC::IsOptimizableMMIOAccess(imm_addr, access_size);

  if (jo.fastmem_arena && is_immediate && PowerPC::IsOptimizableRAMAddress(imm_addr))
  {
    set_addr_reg_if_needed();
    EmitBackpatchRoutine(flags, true, false, dest_reg, XA, BitSet32(0), BitSet32(0));
  }
  else if (mmio_address)
  {
    MMIOLoadToReg(Memory::mmio_mapping.get(), this, regs_in_use, fprs_in_use, dest_reg,
                  mmio_address, flags);
  }
  else
  {
    set_addr_reg_if_needed();
    EmitBackpatchRoutine(flags, jo.fastmem, jo.fastmem, dest_reg, XA, regs_in_use, fprs_in_use);
  }

  gpr.BindToRegister(dest, false, true);
  ASSERT(dest_reg == gpr.R(dest));

  if (update && !early_update)
  {
    gpr.BindToRegister(addr, false);
    set_addr_reg_if_needed();
    MOV(gpr.R(addr), addr_reg);
  }

  gpr.Unlock(ARM64Reg::W0, ARM64Reg::W30);
}

void JitArm64::SafeStoreFromReg(s32 dest, u32 value, s32 regOffset, u32 flags, s32 offset,
                                bool update)
{
  // We want to make sure to not get LR as a temp register
  gpr.Lock(ARM64Reg::W0, ARM64Reg::W1, ARM64Reg::W30);

  ARM64Reg RS = gpr.R(value);

  ARM64Reg reg_dest = ARM64Reg::INVALID_REG;
  ARM64Reg reg_off = ARM64Reg::INVALID_REG;

  if (regOffset != -1 && !gpr.IsImm(regOffset))
    reg_off = gpr.R(regOffset);
  if (dest != -1 && !gpr.IsImm(dest))
    reg_dest = gpr.R(dest);

  ARM64Reg addr_reg = ARM64Reg::W1;

  u32 imm_addr = 0;
  bool is_immediate = false;

  if (regOffset == -1)
  {
    if (dest != -1)
    {
      if (gpr.IsImm(dest))
      {
        is_immediate = true;
        imm_addr = gpr.GetImm(dest) + offset;
      }
      else
      {
        ADDI2R(addr_reg, reg_dest, offset, addr_reg);
      }
    }
    else
    {
      is_immediate = true;
      imm_addr = offset;
    }
  }
  else
  {
    if (dest != -1)
    {
      if (gpr.IsImm(dest) && gpr.IsImm(regOffset))
      {
        is_immediate = true;
        imm_addr = gpr.GetImm(dest) + gpr.GetImm(regOffset);
      }
      else if (gpr.IsImm(dest) && !gpr.IsImm(regOffset))
      {
        u32 reg_offset = gpr.GetImm(dest);
        ADDI2R(addr_reg, reg_off, reg_offset, addr_reg);
      }
      else if (!gpr.IsImm(dest) && gpr.IsImm(regOffset))
      {
        u32 reg_offset = gpr.GetImm(regOffset);
        ADDI2R(addr_reg, reg_dest, reg_offset, addr_reg);
      }
      else
      {
        ADD(addr_reg, reg_dest, reg_off);
      }
    }
    else
    {
      if (gpr.IsImm(regOffset))
      {
        is_immediate = true;
        imm_addr = gpr.GetImm(regOffset);
      }
      else
      {
        MOV(addr_reg, reg_off);
      }
    }
  }

  ARM64Reg XA = EncodeRegTo64(addr_reg);

  bool addr_reg_set = !is_immediate;
  const auto set_addr_reg_if_needed = [&] {
      if (!addr_reg_set)
        MOVI2R(XA, imm_addr);
  };

  const bool early_update = !jo.memcheck && value != static_cast<u32>(dest);
  if (update && early_update)
  {
    gpr.BindToRegister(dest, false);
    set_addr_reg_if_needed();
    MOV(gpr.R(dest), addr_reg);
  }

  BitSet32 regs_in_use = gpr.GetCallerSavedUsed();
  BitSet32 fprs_in_use = fpr.GetCallerSavedUsed();
  regs_in_use[DecodeReg(ARM64Reg::W0)] = 0;
  if (!update || early_update)
    regs_in_use[DecodeReg(ARM64Reg::W1)] = 0;

  u32 access_size = BackPatchInfo::GetFlagSize(flags);
  u32 mmio_address = 0;
  if (is_immediate)
    mmio_address = PowerPC::IsOptimizableMMIOAccess(imm_addr, access_size);

  if (is_immediate && jo.optimizeGatherPipe && PowerPC::IsOptimizableGatherPipeWrite(imm_addr))
  {
    int accessSize;
    if (flags & BackPatchInfo::FLAG_SIZE_32)
      accessSize = 32;
    else if (flags & BackPatchInfo::FLAG_SIZE_16)
      accessSize = 16;
    else
      accessSize = 8;

    LDR(IndexType::Unsigned, ARM64Reg::X0, PPC_REG, PPCSTATE_OFF(gather_pipe_ptr));

    ARM64Reg temp = ARM64Reg::W1;
    temp = ByteswapBeforeStore(this, &m_float_emit, temp, RS, flags, true);

    if (accessSize == 32)
      STR(IndexType::Post, temp, ARM64Reg::X0, 4);
    else if (accessSize == 16)
      STRH(IndexType::Post, temp, ARM64Reg::X0, 2);
    else
      STRB(IndexType::Post, temp, ARM64Reg::X0, 1);

    STR(IndexType::Unsigned, ARM64Reg::X0, PPC_REG, PPCSTATE_OFF(gather_pipe_ptr));

    js.fifoBytesSinceCheck += accessSize >> 3;
  }
  else if (jo.fastmem_arena && is_immediate && PowerPC::IsOptimizableRAMAddress(imm_addr))
  {
    set_addr_reg_if_needed();
    EmitBackpatchRoutine(flags, true, false, RS, XA, BitSet32(0), BitSet32(0));
  }
  else if (mmio_address)
  {
    MMIOWriteRegToAddr(Memory::mmio_mapping.get(), this, regs_in_use, fprs_in_use, RS, mmio_address,
                       flags);
  }
  else
  {
    set_addr_reg_if_needed();
    EmitBackpatchRoutine(flags, jo.fastmem, jo.fastmem, RS, XA, regs_in_use, fprs_in_use);
  }

  if (update && !early_update)
  {
    gpr.BindToRegister(dest, false);
    set_addr_reg_if_needed();
    MOV(gpr.R(dest), addr_reg);
  }

  gpr.Unlock(ARM64Reg::W0, ARM64Reg::W1, ARM64Reg::W30);
}

FixupBranch JitArm64::CheckIfSafeAddress(Arm64Gen::ARM64Reg addr, Arm64Gen::ARM64Reg tmp1,
                                         Arm64Gen::ARM64Reg tmp2)
{
  tmp2 = EncodeRegTo64(tmp2);

  MOVP2R(tmp2, PowerPC::dbat_table.data());
  LSR(tmp1, addr, PowerPC::BAT_INDEX_SHIFT);
  LDR(tmp1, tmp2, ArithOption(tmp1, true));
  FixupBranch pass = TBNZ(tmp1, IntLog2(PowerPC::BAT_PHYSICAL_BIT));
  FixupBranch fail = B();
  SetJumpTarget(pass);
  return fail;
}

void JitArm64::lXX(UGeckoInstruction inst)
{
  INSTRUCTION_START
  JITDISABLE(bJITLoadStoreOff);

  u32 a = inst.RA, b = inst.RB, d = inst.RD;
  s32 offset = inst.SIMM_16;
  s32 offsetReg = -1;
  u32 flags = BackPatchInfo::FLAG_LOAD;
  bool update = false;

  switch (inst.OPCD)
  {
  case 31:
    offsetReg = b;
    switch (inst.SUBOP10)
    {
    case 55:  // lwzux
      update = true;
    case 23:  // lwzx
      flags |= BackPatchInfo::FLAG_SIZE_32;
      break;
    case 119:  // lbzux
      update = true;
    case 87:  // lbzx
      flags |= BackPatchInfo::FLAG_SIZE_8;
      break;
    case 311:  // lhzux
      update = true;
    case 279:  // lhzx
      flags |= BackPatchInfo::FLAG_SIZE_16;
      break;
    case 375:  // lhaux
      update = true;
    case 343:  // lhax
      flags |= BackPatchInfo::FLAG_EXTEND | BackPatchInfo::FLAG_SIZE_16;
      break;
    case 534:  // lwbrx
      flags |= BackPatchInfo::FLAG_REVERSE | BackPatchInfo::FLAG_SIZE_32;
      break;
    case 790:  // lhbrx
      flags |= BackPatchInfo::FLAG_REVERSE | BackPatchInfo::FLAG_SIZE_16;
      break;
    }
    break;
  case 33:  // lwzu
    update = true;
  case 32:  // lwz
    flags |= BackPatchInfo::FLAG_SIZE_32;
    break;
  case 35:  // lbzu
    update = true;
  case 34:  // lbz
    flags |= BackPatchInfo::FLAG_SIZE_8;
    break;
  case 41:  // lhzu
    update = true;
  case 40:  // lhz
    flags |= BackPatchInfo::FLAG_SIZE_16;
    break;
  case 43:  // lhau
    update = true;
  case 42:  // lha
    flags |= BackPatchInfo::FLAG_EXTEND | BackPatchInfo::FLAG_SIZE_16;
    break;
  }

  SafeLoadToReg(d, update ? a : (a ? a : -1), offsetReg, flags, offset, update);
}

void JitArm64::stX(UGeckoInstruction inst)
{
  INSTRUCTION_START
  JITDISABLE(bJITLoadStoreOff);

  u32 a = inst.RA, b = inst.RB, s = inst.RS;
  s32 offset = inst.SIMM_16;
  s32 regOffset = -1;
  u32 flags = BackPatchInfo::FLAG_STORE;
  bool update = false;
  switch (inst.OPCD)
  {
  case 31:
    regOffset = b;
    switch (inst.SUBOP10)
    {
    case 183:  // stwux
      update = true;
      [[fallthrough]];
    case 151:  // stwx
      flags |= BackPatchInfo::FLAG_SIZE_32;
      break;
    case 247:  // stbux
      update = true;
      [[fallthrough]];
    case 215:  // stbx
      flags |= BackPatchInfo::FLAG_SIZE_8;
      break;
    case 439:  // sthux
      update = true;
      [[fallthrough]];
    case 407:  // sthx
      flags |= BackPatchInfo::FLAG_SIZE_16;
      break;
    case 662:  // stwbrx
      flags |= BackPatchInfo::FLAG_REVERSE | BackPatchInfo::FLAG_SIZE_32;
      break;
    case 918:  // sthbrx
      flags |= BackPatchInfo::FLAG_REVERSE | BackPatchInfo::FLAG_SIZE_16;
      break;
    }
    break;
  case 37:  // stwu
    update = true;
  case 36:  // stw
    flags |= BackPatchInfo::FLAG_SIZE_32;
    break;
  case 39:  // stbu
    update = true;
    [[fallthrough]];
  case 38:  // stb
    flags |= BackPatchInfo::FLAG_SIZE_8;
    break;
  case 45:  // sthu
    update = true;
    [[fallthrough]];
  case 44:  // sth
    flags |= BackPatchInfo::FLAG_SIZE_16;
    break;
  }

  SafeStoreFromReg(update ? a : (a ? a : -1), s, regOffset, flags, offset, update);
}

void JitArm64::lmw(UGeckoInstruction inst)
{
  INSTRUCTION_START
  JITDISABLE(bJITLoadStoreOff);

  u32 a = inst.RA, d = inst.RD;
  s32 offset = inst.SIMM_16;

  gpr.Lock(ARM64Reg::W0, ARM64Reg::W30);

  // MMU games make use of a >= d despite this being invalid according to the PEM.
  // Because of this, make sure to not re-read rA after starting doing the loads.
  ARM64Reg addr_reg = ARM64Reg::W0;
  if (a)
  {
    if (gpr.IsImm(a))
      MOVI2R(addr_reg, gpr.GetImm(a) + offset);
    else
      ADDI2R(addr_reg, gpr.R(a), offset, addr_reg);
  }
  else
  {
    MOVI2R(addr_reg, offset);
  }

  // TODO: This doesn't handle rollback on DSI correctly
  constexpr u32 flags = BackPatchInfo::FLAG_LOAD | BackPatchInfo::FLAG_SIZE_32;
  for (u32 i = d; i < 32; i++)
  {
    gpr.BindToRegister(i, false, false);
    ARM64Reg dest_reg = gpr.R(i);

    BitSet32 regs_in_use = gpr.GetCallerSavedUsed();
    BitSet32 fprs_in_use = fpr.GetCallerSavedUsed();
    if (i == 31)
      regs_in_use[DecodeReg(addr_reg)] = 0;
    if (!jo.memcheck)
      regs_in_use[DecodeReg(dest_reg)] = 0;

    EmitBackpatchRoutine(flags, jo.fastmem, jo.fastmem, dest_reg, EncodeRegTo64(addr_reg),
                         regs_in_use, fprs_in_use);

    gpr.BindToRegister(i, false, true);
    ASSERT(dest_reg == gpr.R(i));

    if (i != 31)
      ADD(addr_reg, addr_reg, 4);
  }

  gpr.Unlock(ARM64Reg::W0, ARM64Reg::W30);
}

void JitArm64::stmw(UGeckoInstruction inst)
{
  INSTRUCTION_START
  JITDISABLE(bJITLoadStoreOff);

  u32 a = inst.RA, s = inst.RS;
  s32 offset = inst.SIMM_16;

  gpr.Lock(ARM64Reg::W0, ARM64Reg::W1, ARM64Reg::W30);

  ARM64Reg addr_reg = ARM64Reg::W1;
  if (a)
  {
    if (gpr.IsImm(a))
      MOVI2R(addr_reg, gpr.GetImm(a) + offset);
    else
      ADDI2R(addr_reg, gpr.R(a), offset, addr_reg);
  }
  else
  {
    MOVI2R(addr_reg, offset);
  }

  // TODO: This doesn't handle rollback on DSI correctly
  constexpr u32 flags = BackPatchInfo::FLAG_STORE | BackPatchInfo::FLAG_SIZE_32;
  for (u32 i = s; i < 32; i++)
  {
    ARM64Reg src_reg = gpr.R(i);

    BitSet32 regs_in_use = gpr.GetCallerSavedUsed();
    BitSet32 fprs_in_use = fpr.GetCallerSavedUsed();
    regs_in_use[DecodeReg(ARM64Reg::W0)] = 0;
    if (i == 31)
      regs_in_use[DecodeReg(addr_reg)] = 0;

    EmitBackpatchRoutine(flags, jo.fastmem, jo.fastmem, src_reg, EncodeRegTo64(addr_reg),
                         regs_in_use, fprs_in_use);

    if (i != 31)
      ADD(addr_reg, addr_reg, 4);
  }

  gpr.Unlock(ARM64Reg::W0, ARM64Reg::W1, ARM64Reg::W30);
}

void JitArm64::dcbx(UGeckoInstruction inst)
{
  INSTRUCTION_START
  JITDISABLE(bJITLoadStoreOff);

  gpr.Lock(ARM64Reg::W0);

  ARM64Reg addr = ARM64Reg::W0;

  u32 a = inst.RA, b = inst.RB;

  if (a)
    ADD(addr, gpr.R(a), gpr.R(b));
  else
    MOV(addr, gpr.R(b));

  ANDI2R(addr, addr, ~31);  // mask sizeof cacheline

  BitSet32 gprs_to_push = gpr.GetCallerSavedUsed();
  BitSet32 fprs_to_push = fpr.GetCallerSavedUsed();

  ABI_PushRegisters(gprs_to_push);
  m_float_emit.ABI_PushRegisters(fprs_to_push, ARM64Reg::X30);

  MOVI2R(ARM64Reg::X1, 32);
  MOVI2R(ARM64Reg::X2, 0);
  MOVP2R(ARM64Reg::X3, &JitInterface::InvalidateICache);
  BLR(ARM64Reg::X3);

  m_float_emit.ABI_PopRegisters(fprs_to_push, ARM64Reg::X30);
  ABI_PopRegisters(gprs_to_push);

  gpr.Unlock(ARM64Reg::W0);
}

void JitArm64::dcbt(UGeckoInstruction inst)
{
  INSTRUCTION_START
  JITDISABLE(bJITLoadStoreOff);

  // Prefetch. Since we don't emulate the data cache, we don't need to do anything.

  // If a dcbst follows a dcbt, it probably isn't a case of dynamic code
  // modification, so don't bother invalidating the jit block cache.
  // This is important because invalidating the block cache when we don't
  // need to is terrible for performance.
  // (Invalidating the jit block cache on dcbst is a heuristic.)
  if (CanMergeNextInstructions(1) && js.op[1].inst.OPCD == 31 && js.op[1].inst.SUBOP10 == 54 &&
      js.op[1].inst.RA == inst.RA && js.op[1].inst.RB == inst.RB)
  {
    js.skipInstructions = 1;
  }
}

void JitArm64::dcbz(UGeckoInstruction inst)
{
  INSTRUCTION_START
  JITDISABLE(bJITLoadStoreOff);
  FALLBACK_IF(SConfig::GetInstance().bLowDCBZHack);

  int a = inst.RA, b = inst.RB;

  gpr.Lock(ARM64Reg::W0);

  ARM64Reg addr_reg = ARM64Reg::W0;

  if (a)
  {
    bool is_imm_a, is_imm_b;
    is_imm_a = gpr.IsImm(a);
    is_imm_b = gpr.IsImm(b);
    if (is_imm_a && is_imm_b)
    {
      // full imm_addr
      u32 imm_addr = gpr.GetImm(b) + gpr.GetImm(a);
      MOVI2R(addr_reg, imm_addr & ~31);
    }
    else if (is_imm_a || is_imm_b)
    {
      // Only one register is an immediate
      ARM64Reg base = is_imm_a ? gpr.R(b) : gpr.R(a);
      u32 imm_offset = is_imm_a ? gpr.GetImm(a) : gpr.GetImm(b);
      ADDI2R(addr_reg, base, imm_offset, addr_reg);
      ANDI2R(addr_reg, addr_reg, ~31);
    }
    else
    {
      // Both are registers
      ADD(addr_reg, gpr.R(a), gpr.R(b));
      ANDI2R(addr_reg, addr_reg, ~31);
    }
  }
  else
  {
    // RA isn't used, only RB
    if (gpr.IsImm(b))
    {
      u32 imm_addr = gpr.GetImm(b);
      MOVI2R(addr_reg, imm_addr & ~31);
    }
    else
    {
      ANDI2R(addr_reg, gpr.R(b), ~31);
    }
  }

  // We don't care about being /too/ terribly efficient here
  // As long as we aren't falling back to interpreter we're winning a lot

  BitSet32 gprs_to_push = gpr.GetCallerSavedUsed();
  BitSet32 fprs_to_push = fpr.GetCallerSavedUsed();
  gprs_to_push[DecodeReg(ARM64Reg::W0)] = 0;

  EmitBackpatchRoutine(BackPatchInfo::FLAG_ZERO_256, jo.fastmem, jo.fastmem, ARM64Reg::W0,
                       EncodeRegTo64(addr_reg), gprs_to_push, fprs_to_push);

  gpr.Unlock(ARM64Reg::W0);
}

void JitArm64::eieio(UGeckoInstruction inst)
{
  INSTRUCTION_START
  JITDISABLE(bJITLoadStoreOff);

  // optimizeGatherPipe generally postpones FIFO checks to the end of the JIT block,
  // which is generally safe. However postponing FIFO writes across eieio instructions
  // is incorrect (would crash NBA2K11 strap screen if we improve our FIFO detection).
  if (jo.optimizeGatherPipe && js.fifoBytesSinceCheck > 0)
    js.mustCheckFifo = true;
}
