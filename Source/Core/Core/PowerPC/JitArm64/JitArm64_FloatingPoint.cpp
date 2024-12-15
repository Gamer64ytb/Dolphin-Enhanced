// Copyright 2015 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "Common/Arm64Emitter.h"
#include "Common/CommonTypes.h"
#include "Common/StringUtil.h"

#include "Core/ConfigManager.h"
#include "Core/Core.h"
#include "Core/CoreTiming.h"
#include "Core/PowerPC/Gekko.h"
#include "Core/PowerPC/JitArm64/Jit.h"
#include "Core/PowerPC/JitArm64/JitArm64_RegCache.h"
#include "Core/PowerPC/PPCTables.h"
#include "Core/PowerPC/PowerPC.h"

using namespace Arm64Gen;

void JitArm64::SetFPRFIfNeeded(bool single, ARM64Reg reg)
{
  if (!SConfig::GetInstance().bFPRF || !js.op->wantsFPRF)
    return;

  gpr.Lock(ARM64Reg::W0, ARM64Reg::W1, ARM64Reg::W2, ARM64Reg::W3, ARM64Reg::W4, ARM64Reg::W30);

  const ARM64Reg routine_input_reg = single ? ARM64Reg::W0 : ARM64Reg::X0;
  if (IsVector(reg))
  {
    m_float_emit.FMOV(routine_input_reg, single ? EncodeRegToSingle(reg) : EncodeRegToDouble(reg));
  }
  else if (reg != routine_input_reg)
  {
    MOV(routine_input_reg, reg);
  }

  BL(single ? GetAsmRoutines()->fprf_single : GetAsmRoutines()->fprf_double);

  gpr.Unlock(ARM64Reg::W0, ARM64Reg::W1, ARM64Reg::W2, ARM64Reg::W3, ARM64Reg::W4, ARM64Reg::W30);
}

void JitArm64::fp_arith(UGeckoInstruction inst)
{
  INSTRUCTION_START
  JITDISABLE(bJITFloatingPointOff);
  FALLBACK_IF(inst.Rc);

  u32 a = inst.FA, b = inst.FB, c = inst.FC, d = inst.FD;
  u32 op5 = inst.SUBOP5;

  bool single = inst.OPCD == 59;
  bool packed = inst.OPCD == 4;

  bool use_c = op5 >= 25;  // fmul and all kind of fmaddXX
  bool use_b = op5 != 25;  // fmul uses no B

  const auto inputs_are_singles_func = [&] {
    return fpr.IsSingle(a, !packed) && (!use_b || fpr.IsSingle(b, !packed)) &&
           (!use_c || fpr.IsSingle(c, !packed));
  };
  const bool inputs_are_singles = inputs_are_singles_func();

  ARM64Reg VA, VB, VC, VD;

  if (packed)
  {
    const RegType type = inputs_are_singles ? RegType::Single : RegType::Register;
    const u8 size = inputs_are_singles ? 32 : 64;
    const auto reg_encoder = inputs_are_singles ? EncodeRegToDouble : EncodeRegToQuad;

    VA = reg_encoder(fpr.R(a, type));
    if (use_b)
      VB = reg_encoder(fpr.R(b, type));
    if (use_c)
      VC = reg_encoder(fpr.R(c, type));
    VD = reg_encoder(fpr.RW(d, type));

    switch (op5)
    {
    case 18:
      m_float_emit.FDIV(size, VD, VA, VB);
      break;
    case 20:
      m_float_emit.FSUB(size, VD, VA, VB);
      break;
    case 21:
      m_float_emit.FADD(size, VD, VA, VB);
      break;
    case 25:
      m_float_emit.FMUL(size, VD, VA, VC);
      break;
    default:
      ASSERT_MSG(DYNA_REC, 0, "fp_arith");
      break;
    }
  }
  else
  {
    const RegType type = (inputs_are_singles && single) ? RegType::LowerPairSingle : RegType::LowerPair;
    const RegType type_out = single ? (inputs_are_singles ? RegType::DuplicatedSingle : RegType::Duplicated) : RegType::LowerPair;
    const auto reg_encoder = (inputs_are_singles && single) ? EncodeRegToSingle : EncodeRegToDouble;

    VA = reg_encoder(fpr.R(a, type));
    if (use_b)
      VB = reg_encoder(fpr.R(b, type));
    if (use_c)
      VC = reg_encoder(fpr.R(c, type));
    VD = reg_encoder(fpr.RW(d, type_out));

    switch (op5)
    {
    case 18:
      m_float_emit.FDIV(VD, VA, VB);
      break;
    case 20:
      m_float_emit.FSUB(VD, VA, VB);
      break;
    case 21:
      m_float_emit.FADD(VD, VA, VB);
      break;
    case 25:
      m_float_emit.FMUL(VD, VA, VC);
      break;
    case 28:
      m_float_emit.FNMSUB(VD, VA, VC, VB);
      break;  // fmsub: "D = A*C - B" vs "Vd = (-Va) + Vn*Vm"
    case 29:
      m_float_emit.FMADD(VD, VA, VC, VB);
      break;  // fmadd: "D = A*C + B" vs "Vd = Va + Vn*Vm"
    case 30:
      m_float_emit.FMSUB(VD, VA, VC, VB);
      break;  // fnmsub: "D = -(A*C - B)" vs "Vd = Va + (-Vn)*Vm"
    case 31:
      m_float_emit.FNMADD(VD, VA, VC, VB);
      break;  // fnmadd: "D = -(A*C + B)" vs "Vd = (-Va) + (-Vn)*Vm"
    default:
      ASSERT_MSG(DYNA_REC, 0, "fp_arith");
      break;
    }
  }

  const bool outputs_are_singles = single || packed;

  if (outputs_are_singles)
  {
    ASSERT_MSG(DYNA_REC, inputs_are_singles == inputs_are_singles_func(),
               "Register allocation turned singles into doubles in the middle of fp_arith");

    fpr.FixSinglePrecision(d);
  }

  SetFPRFIfNeeded(outputs_are_singles, VD);
}

void JitArm64::fp_logic(UGeckoInstruction inst)
{
  INSTRUCTION_START
  JITDISABLE(bJITFloatingPointOff);
  FALLBACK_IF(inst.Rc);

  const u32 b = inst.FB;
  const u32 d = inst.FD;
  const u32 op10 = inst.SUBOP10;

  bool packed = inst.OPCD == 4;

  // MR with source === dest => no-op
  if (op10 == 72 && b == d)
    return;

  const bool single = fpr.IsSingle(b, !packed);
  const u8 size = single ? 32 : 64;

  if (packed)
  {
    const RegType type = single ? RegType::Single : RegType::Register;
    const auto reg_encoder = single ? EncodeRegToDouble : EncodeRegToQuad;

    const ARM64Reg VB = reg_encoder(fpr.R(b, type));
    const ARM64Reg VD = reg_encoder(fpr.RW(d, type));

    switch (op10)
    {
    case 40:
      m_float_emit.FNEG(size, VD, VB);
      break;
    case 72:
      m_float_emit.ORR(VD, VB, VB);
      break;
    case 136:
      m_float_emit.FABS(size, VD, VB);
      m_float_emit.FNEG(size, VD, VD);
      break;
    case 264:
      m_float_emit.FABS(size, VD, VB);
      break;
    default:
      ASSERT_MSG(DYNA_REC, 0, "fp_logic");
      break;
    }
  }
  else
  {
    const RegType type = single ? RegType::LowerPairSingle : RegType::LowerPair;
    const auto reg_encoder = single ? EncodeRegToSingle : EncodeRegToDouble;

    const ARM64Reg VB = fpr.R(b, type);
    const ARM64Reg VD = fpr.RW(d, type);

    switch (op10)
    {
    case 40:
      m_float_emit.FNEG(reg_encoder(VD), reg_encoder(VB));
      break;
    case 72:
      m_float_emit.INS(size, VD, 0, VB, 0);
      break;
    case 136:
      m_float_emit.FABS(reg_encoder(VD), reg_encoder(VB));
      m_float_emit.FNEG(reg_encoder(VD), reg_encoder(VD));
      break;
    case 264:
      m_float_emit.FABS(reg_encoder(VD), reg_encoder(VB));
      break;
    default:
      ASSERT_MSG(DYNA_REC, 0, "fp_logic");
      break;
    }
  }

  ASSERT_MSG(DYNA_REC, single == fpr.IsSingle(b, !packed),
             "Register allocation turned singles into doubles in the middle of fp_logic");
}

void JitArm64::fselx(UGeckoInstruction inst)
{
  INSTRUCTION_START
  JITDISABLE(bJITFloatingPointOff);
  FALLBACK_IF(inst.Rc);

  const u32 a = inst.FA;
  const u32 b = inst.FB;
  const u32 c = inst.FC;
  const u32 d = inst.FD;

  const bool b_and_c_singles = fpr.IsSingle(b, true) && fpr.IsSingle(c, true);
  const RegType b_and_c_type = b_and_c_singles ? RegType::LowerPairSingle : RegType::LowerPair;
  const auto b_and_c_reg_encoder = b_and_c_singles ? EncodeRegToSingle : EncodeRegToDouble;

  const bool a_single = fpr.IsSingle(a, true) && (b_and_c_singles || (a != b && a != c));
  const RegType a_type = a_single ? RegType::LowerPairSingle : RegType::LowerPair;
  const auto a_reg_encoder = a_single ? EncodeRegToSingle : EncodeRegToDouble;

  const ARM64Reg VA = fpr.R(a, a_type);
  const ARM64Reg VB = fpr.R(b, b_and_c_type);
  const ARM64Reg VC = fpr.R(c, b_and_c_type);

  // If a == d, the RW call below may change the type of a to double. This is okay, because the
  // actual value in the register is not altered by RW. So let's just assert before calling RW.
  ASSERT_MSG(DYNA_REC, a_single == fpr.IsSingle(a, true),
             "Register allocation turned singles into doubles in the middle of fselx");

  const ARM64Reg VD = fpr.RW(d, b_and_c_type);

  m_float_emit.FCMPE(a_reg_encoder(VA));
  m_float_emit.FCSEL(b_and_c_reg_encoder(VD), b_and_c_reg_encoder(VC), b_and_c_reg_encoder(VB),
                     CC_GE);

  ASSERT_MSG(DYNA_REC, b_and_c_singles == (fpr.IsSingle(b, true) && fpr.IsSingle(c, true)),
             "Register allocation turned singles into doubles in the middle of fselx");
}

void JitArm64::frspx(UGeckoInstruction inst)
{
  INSTRUCTION_START
  JITDISABLE(bJITFloatingPointOff);
  FALLBACK_IF(inst.Rc);

  const u32 b = inst.FB;
  const u32 d = inst.FD;

  const bool single = fpr.IsSingle(b, true);
  if (single && js.fpr_is_store_safe[b])
  {
    // Source is already in single precision, so no need to do anything but to copy to PSR1.
    const ARM64Reg VB = fpr.R(b, RegType::LowerPairSingle);
    const ARM64Reg VD = fpr.RW(d, RegType::DuplicatedSingle);

    if (b != d)
      m_float_emit.FMOV(EncodeRegToSingle(VD), EncodeRegToSingle(VB));

    ASSERT_MSG(DYNA_REC, fpr.IsSingle(b, true),
               "Register allocation turned singles into doubles in the middle of frspx");

    SetFPRFIfNeeded(true, VD);
  }
  else
  {
    const ARM64Reg VB = fpr.R(b, RegType::LowerPair);
    const ARM64Reg VD = fpr.RW(d, RegType::DuplicatedSingle);

    m_float_emit.FCVT(32, 64, EncodeRegToDouble(VD), EncodeRegToDouble(VB));

    SetFPRFIfNeeded(true, VD);
  }
}

// fcmpo, Floating Compare Ordered
// An ordered comparison checks if neither operand is NaN.
//
// fcmpu, Floating Compare Unordered
// An unordered comparison checks if either operand is a NaN.
//
void JitArm64::fcmpX(UGeckoInstruction inst)
{
  INSTRUCTION_START
  JITDISABLE(bJITFloatingPointOff);

  const bool fprf = SConfig::GetInstance().bFPRF && js.op->wantsFPRF;

  const u32 a = inst.FA;
  const u32 b = inst.FB;
  const int crf = inst.CRFD;

  const bool singles = fpr.IsSingle(a, true) && fpr.IsSingle(b, true);

  // fcmpo, temp fx for SD Gundam
  FALLBACK_IF(a != b && inst.SUBOP10 == 32);

  const RegType type = singles ? RegType::LowerPairSingle : RegType::LowerPair;
  const auto reg_encoder = singles ? EncodeRegToSingle : EncodeRegToDouble;

  const ARM64Reg VA = reg_encoder(fpr.R(a, type));
  const ARM64Reg VB = reg_encoder(fpr.R(b, type));

  gpr.BindCRToRegister(crf, false);
  const ARM64Reg XA = gpr.CR(crf);

  ARM64Reg fpscr_reg;
  if (fprf)
  {
    fpscr_reg = gpr.GetReg();
    LDR(IndexType::Unsigned, fpscr_reg, PPC_REG, PPCSTATE_OFF(fpscr));
    ANDI2R(fpscr_reg, fpscr_reg, ~FPRF_MASK);
  }

  FixupBranch pNaN, pLesser, pGreater;
  FixupBranch continue1, continue2, continue3;
  ORR(XA, ARM64Reg::ZR, 32, 0, true);

  m_float_emit.FCMP(VA, VB);

  if (a != b)
  {
    // if B > A goto Greater's jump target
    pGreater = B(CC_GT);
    // if B < A, goto Lesser's jump target
    pLesser = B(CC_MI);
  }

  pNaN = B(CC_VS);

  // A == B
  ORR(XA, XA, 64 - 63, 0, true);
  if (fprf)
    ORRI2R(fpscr_reg, fpscr_reg, PowerPC::CR_EQ << FPRF_SHIFT);

  continue1 = B();

  SetJumpTarget(pNaN);

  MOVI2R(XA, PowerPC::ConditionRegister::PPCToInternal(PowerPC::CR_SO));
  if (fprf)
    ORRI2R(fpscr_reg, fpscr_reg, PowerPC::CR_SO << FPRF_SHIFT);

  if (a != b)
  {
    continue2 = B();

    SetJumpTarget(pGreater);
    ORR(XA, XA, 0, 0, true);
    if (fprf)
      ORRI2R(fpscr_reg, fpscr_reg, PowerPC::CR_GT << FPRF_SHIFT);

    continue3 = B();

    SetJumpTarget(pLesser);
    ORR(XA, XA, 64 - 62, 1, true);
    ORR(XA, XA, 0, 0, true);
    if (fprf)
      ORRI2R(fpscr_reg, fpscr_reg, PowerPC::CR_LT << FPRF_SHIFT);

    SetJumpTarget(continue2);
    SetJumpTarget(continue3);
  }
  SetJumpTarget(continue1);

  ASSERT_MSG(DYNA_REC, singles == (fpr.IsSingle(a, true) && fpr.IsSingle(b, true)),
             "Register allocation turned singles into doubles in the middle of fcmpX");

  if (fprf)
  {
    STR(IndexType::Unsigned, fpscr_reg, PPC_REG, PPCSTATE_OFF(fpscr));
    gpr.Unlock(fpscr_reg);
  }
}

// fctiw: Floating Convert to Integer Word
// fctiwz: Floating Convert to Integer Word with Round toward Zero
void JitArm64::fctiwzx(UGeckoInstruction inst)
{
  INSTRUCTION_START
  JITDISABLE(bJITFloatingPointOff);
  FALLBACK_IF(inst.Rc);

  const u32 b = inst.FB;
  const u32 d = inst.FD;

  const bool single = fpr.IsSingle(b, true);
  // temp fix for eternal darkness
  FALLBACK_IF(!single);

  const ARM64Reg VB = fpr.R(b, single ? RegType::LowerPairSingle : RegType::LowerPair);
  const ARM64Reg VD = fpr.RW(d, RegType::LowerPair);

  const ARM64Reg V0 = fpr.GetReg();

  // Generate 0xFFF8000000000000ULL
  m_float_emit.MOVI(64, EncodeRegToDouble(V0), 0xFFFF000000000000ULL);
  m_float_emit.BIC(16, EncodeRegToDouble(V0), 0x7);

  if (single)
  {
    m_float_emit.FCVTS(EncodeRegToSingle(VD), EncodeRegToSingle(VB), ROUND_Z);
  }
  else
  {
    ARM64Reg WA = gpr.GetReg();

    m_float_emit.FCVTS(WA, EncodeRegToDouble(VB), ROUND_Z);
    m_float_emit.FMOV(EncodeRegToSingle(VD), WA);

    gpr.Unlock(WA);
  }
  m_float_emit.ORR(EncodeRegToDouble(VD), EncodeRegToDouble(VD), EncodeRegToDouble(V0));
  fpr.Unlock(V0);

  ASSERT_MSG(DYNA_REC, b == d || single == fpr.IsSingle(b, true),
             "Register allocation turned singles into doubles in the middle of fctiwzx");
}

void JitArm64::fresx(UGeckoInstruction inst)
{
  INSTRUCTION_START
  JITDISABLE(bJITFloatingPointOff);
  FALLBACK_IF(inst.Rc);

  const u32 b = inst.FB;
  const u32 d = inst.FD;

  gpr.Lock(ARM64Reg::W0, ARM64Reg::W1, ARM64Reg::W2, ARM64Reg::W3, ARM64Reg::W4, ARM64Reg::W30);
  fpr.Lock(ARM64Reg::Q0);

  const ARM64Reg VB = fpr.R(b, RegType::LowerPair);
  m_float_emit.FMOV(ARM64Reg::X1, EncodeRegToDouble(VB));
  m_float_emit.FRECPE(ARM64Reg::D0, EncodeRegToDouble(VB));

  BL(GetAsmRoutines()->fres);

  gpr.Unlock(ARM64Reg::W0, ARM64Reg::W1, ARM64Reg::W2, ARM64Reg::W3, ARM64Reg::W4, ARM64Reg::W30);
  fpr.Unlock(ARM64Reg::Q0);

  const ARM64Reg VD = fpr.RW(d, RegType::Duplicated);
  m_float_emit.FMOV(EncodeRegToDouble(VD), ARM64Reg::X0);

  SetFPRFIfNeeded(false, ARM64Reg::X0);
}

void JitArm64::frsqrtex(UGeckoInstruction inst)
{
  INSTRUCTION_START
  JITDISABLE(bJITFloatingPointOff);
  FALLBACK_IF(inst.Rc);

  const u32 b = inst.FB;
  const u32 d = inst.FD;

  gpr.Lock(ARM64Reg::W0, ARM64Reg::W1, ARM64Reg::W2, ARM64Reg::W3, ARM64Reg::W4, ARM64Reg::W30);
  fpr.Lock(ARM64Reg::Q0);

  const ARM64Reg VB = fpr.R(b, RegType::LowerPair);
  m_float_emit.FMOV(ARM64Reg::X1, EncodeRegToDouble(VB));
  m_float_emit.FRSQRTE(ARM64Reg::D0, EncodeRegToDouble(VB));

  BL(GetAsmRoutines()->frsqrte);

  gpr.Unlock(ARM64Reg::W0, ARM64Reg::W1, ARM64Reg::W2, ARM64Reg::W3, ARM64Reg::W4, ARM64Reg::W30);
  fpr.Unlock(ARM64Reg::Q0);

  const ARM64Reg VD = fpr.RW(d, RegType::LowerPair);
  m_float_emit.FMOV(EncodeRegToDouble(VD), ARM64Reg::X0);

  SetFPRFIfNeeded(false, ARM64Reg::X0);
}

// Since the following float conversion functions are used in non-arithmetic PPC float
// instructions, they must convert floats bitexact and never flush denormals to zero or turn SNaNs
// into QNaNs. This means we can't just use FCVT/FCVTL/FCVTN.

void JitArm64::ConvertDoubleToSingleLower(size_t guest_reg, ARM64Reg dest_reg, ARM64Reg src_reg)
{
  if (js.fpr_is_store_safe[guest_reg])
  {
    m_float_emit.FCVT(32, 64, EncodeRegToDouble(dest_reg), EncodeRegToDouble(src_reg));
    return;
  }

  FlushCarry();

  const BitSet32 gpr_saved = gpr.GetCallerSavedUsed() & BitSet32{0, 1, 2, 3, 30};
  ABI_PushRegisters(gpr_saved);

  m_float_emit.UMOV(64, ARM64Reg::X0, src_reg, 0);
  BL(cdts);
  m_float_emit.INS(32, dest_reg, 0, ARM64Reg::W1);

  ABI_PopRegisters(gpr_saved);
}

void JitArm64::ConvertDoubleToSinglePair(size_t guest_reg, ARM64Reg dest_reg, ARM64Reg src_reg)
{
  if (js.fpr_is_store_safe[guest_reg])
  {
    m_float_emit.FCVTN(32, EncodeRegToDouble(dest_reg), EncodeRegToDouble(src_reg));
    return;
  }

  FlushCarry();

  const BitSet32 gpr_saved = gpr.GetCallerSavedUsed() & BitSet32{0, 1, 2, 3, 30};
  ABI_PushRegisters(gpr_saved);

  m_float_emit.UMOV(64, ARM64Reg::X0, src_reg, 0);
  BL(cdts);
  m_float_emit.INS(32, dest_reg, 0, ARM64Reg::W1);

  m_float_emit.UMOV(64, ARM64Reg::X0, src_reg, 1);
  BL(cdts);
  m_float_emit.INS(32, dest_reg, 1, ARM64Reg::W1);

  ABI_PopRegisters(gpr_saved);
}

void JitArm64::ConvertSingleToDoubleLower(size_t guest_reg, ARM64Reg dest_reg, ARM64Reg src_reg,
                                          ARM64Reg scratch_reg)
{
  ASSERT(scratch_reg != src_reg);

  if (js.fpr_is_store_safe[guest_reg])
  {
    m_float_emit.FCVT(64, 32, EncodeRegToDouble(dest_reg), EncodeRegToDouble(src_reg));
    return;
  }

  const bool switch_to_farcode = !IsInFarCode();

  FlushCarry();

  // Do we know that the input isn't NaN, and that the input isn't denormal or FPCR.FZ is not set?
  // (This check unfortunately also catches zeroes)

  FixupBranch fast;
  if (scratch_reg != ARM64Reg::INVALID_REG)
  {
    m_float_emit.FABS(EncodeRegToSingle(scratch_reg), EncodeRegToSingle(src_reg));
    m_float_emit.FCMP(EncodeRegToSingle(scratch_reg));
    fast = B(CCFlags::CC_GT);

    if (switch_to_farcode)
    {
      FixupBranch slow = B();

      SwitchToFarCode();
      SetJumpTarget(slow);
    }
  }

  // If no (or if we don't have a scratch register), call the bit-exact routine

  const BitSet32 gpr_saved = gpr.GetCallerSavedUsed() & BitSet32{0, 1, 2, 3, 4, 30};
  ABI_PushRegisters(gpr_saved);

  m_float_emit.UMOV(32, ARM64Reg::W0, src_reg, 0);
  BL(cstd);
  m_float_emit.INS(64, dest_reg, 0, ARM64Reg::X0);

  ABI_PopRegisters(gpr_saved);

  // If yes, do a fast conversion with FCVT

  if (scratch_reg != ARM64Reg::INVALID_REG)
  {
    FixupBranch continue1 = B();

    if (switch_to_farcode)
      SwitchToNearCode();

    SetJumpTarget(fast);

    m_float_emit.FCVT(64, 32, EncodeRegToDouble(dest_reg), EncodeRegToDouble(src_reg));

    SetJumpTarget(continue1);
  }
}

void JitArm64::ConvertSingleToDoublePair(size_t guest_reg, ARM64Reg dest_reg, ARM64Reg src_reg,
                                         ARM64Reg scratch_reg)
{
  ASSERT(scratch_reg != src_reg);

  if (js.fpr_is_store_safe[guest_reg])
  {
    m_float_emit.FCVTL(64, EncodeRegToDouble(dest_reg), EncodeRegToDouble(src_reg));
    return;
  }

  const bool switch_to_farcode = !IsInFarCode();

  FlushCarry();

  // Do we know that neither input is NaN, and that neither input is denormal or FPCR.FZ is not set?
  // (This check unfortunately also catches zeroes)

  FixupBranch fast;
  if (scratch_reg != ARM64Reg::INVALID_REG)
  {
    // Set each 32-bit element of scratch_reg to 0x0000'0000 or 0xFFFF'FFFF depending on whether
    // the absolute value of the corresponding element in src_reg compares greater than 0
    m_float_emit.MOVI(8, EncodeRegToDouble(scratch_reg), 0);
    m_float_emit.FACGT(32, EncodeRegToDouble(scratch_reg), EncodeRegToDouble(src_reg),
                       EncodeRegToDouble(scratch_reg));

    // 0x0000'0000'0000'0000 (zero)     -> 0x0000'0000'0000'0000 (zero)
    // 0x0000'0000'FFFF'FFFF (denormal) -> 0xFF00'0000'FFFF'FFFF (normal)
    // 0xFFFF'FFFF'0000'0000 (NaN)      -> 0x00FF'FFFF'0000'0000 (normal)
    // 0xFFFF'FFFF'FFFF'FFFF (NaN)      -> 0xFFFF'FFFF'FFFF'FFFF (NaN)
    m_float_emit.INS(8, EncodeRegToDouble(scratch_reg), 7, EncodeRegToDouble(scratch_reg), 0);

    // Is scratch_reg a NaN (0xFFFF'FFFF'FFFF'FFFF)?
    m_float_emit.FCMP(EncodeRegToDouble(scratch_reg));
    fast = B(CCFlags::CC_VS);

    if (switch_to_farcode)
    {
      FixupBranch slow = B();

      SwitchToFarCode();
      SetJumpTarget(slow);
    }
  }

  // If no (or if we don't have a scratch register), call the bit-exact routine

  // Save X0-X4 and X30 if they're in use
  const BitSet32 gpr_saved = gpr.GetCallerSavedUsed() & BitSet32{0, 1, 2, 3, 4, 30};
  ABI_PushRegisters(gpr_saved);

  m_float_emit.UMOV(32, ARM64Reg::W0, src_reg, 1);
  BL(cstd);
  m_float_emit.INS(64, dest_reg, 1, ARM64Reg::X0);

  m_float_emit.UMOV(32, ARM64Reg::W0, src_reg, 0);
  BL(cstd);
  m_float_emit.INS(64, dest_reg, 0, ARM64Reg::X0);

  ABI_PopRegisters(gpr_saved);

  // If yes, do a fast conversion with FCVTL

  if (scratch_reg != ARM64Reg::INVALID_REG)
  {
    FixupBranch continue1 = B();

    if (switch_to_farcode)
      SwitchToNearCode();

    SetJumpTarget(fast);
    m_float_emit.FCVTL(64, EncodeRegToDouble(dest_reg), EncodeRegToDouble(src_reg));

    SetJumpTarget(continue1);
  }
}

bool JitArm64::IsFPRStoreSafe(size_t guest_reg) const
{
  return js.fpr_is_store_safe[guest_reg];
}
