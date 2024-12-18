// Copyright 2014 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include <cinttypes>
#include <cstddef>
#include <optional>
#include <string>

#include "Common/BitSet.h"
#include "Common/CommonFuncs.h"
#include "Common/CommonTypes.h"
#include "Common/Logging/Log.h"
#include "Common/MathUtil.h"
#include "Common/StringUtil.h"
#include "Common/Swap.h"

#include "Core/HW/Memmap.h"
#include "Core/PowerPC/JitArm64/Jit.h"
#include "Core/PowerPC/JitArm64/Jit_Util.h"
#include "Core/PowerPC/JitArmCommon/BackPatch.h"
#include "Core/PowerPC/MMU.h"
#include "Core/PowerPC/PowerPC.h"

using namespace Arm64Gen;

void JitArm64::DoBacktrace(uintptr_t access_address, SContext* ctx)
{
  for (int i = 0; i < 30; i += 2)
    ERROR_LOG(DYNA_REC, "R%d: 0x%016llx\tR%d: 0x%016llx", i, ctx->CTX_REG(i), i + 1,
              ctx->CTX_REG(i + 1));

  ERROR_LOG(DYNA_REC, "R30: 0x%016llx\tSP: 0x%016llx", ctx->CTX_REG(30), ctx->CTX_SP);

  ERROR_LOG(DYNA_REC, "Access Address: 0x%016lx", access_address);
  ERROR_LOG(DYNA_REC, "PC: 0x%016llx", ctx->CTX_PC);

  ERROR_LOG(DYNA_REC, "Memory Around PC");

  std::string pc_memory;
  for (u64 pc = (ctx->CTX_PC - 32); pc < (ctx->CTX_PC + 32); pc += 16)
  {
    pc_memory += StringFromFormat("%08x%08x%08x%08x", Common::swap32(*(u32*)pc),
                                  Common::swap32(*(u32*)(pc + 4)), Common::swap32(*(u32*)(pc + 8)),
                                  Common::swap32(*(u32*)(pc + 12)));

    ERROR_LOG(DYNA_REC, "0x%016" PRIx64 ": %08x %08x %08x %08x", pc, *(u32*)pc, *(u32*)(pc + 4),
              *(u32*)(pc + 8), *(u32*)(pc + 12));
  }

  ERROR_LOG(DYNA_REC, "Full block: %s", pc_memory.c_str());
}

void JitArm64::EmitBackpatchRoutine(u32 flags, bool fastmem, bool do_farcode, ARM64Reg RS,
                                    ARM64Reg addr, BitSet32 gprs_to_push, BitSet32 fprs_to_push,
                                    bool emitting_routine)
{
  bool in_far_code = false;
  const u8* fastmem_start = GetCodePtr();
  std::optional<FixupBranch> slowmem_fixup;

  if (fastmem)
  {
    if (do_farcode && emitting_routine)
    {
      const ARM64Reg temp1 = flags & BackPatchInfo::FLAG_STORE ? ARM64Reg::W0 : ARM64Reg::W3;
      const ARM64Reg temp2 = ARM64Reg::W2;

      slowmem_fixup = CheckIfSafeAddress(addr, temp1, temp2);
    }
    
    if ((flags & BackPatchInfo::FLAG_STORE) && (flags & BackPatchInfo::FLAG_FLOAT))
    {
      ARM64Reg temp = ARM64Reg::D0;
      temp = ByteswapBeforeStore(this, &m_float_emit, temp, EncodeRegToDouble(RS), flags, true);

      m_float_emit.STR(BackPatchInfo::GetFlagSize(flags), temp, MEM_REG, addr);
    }
    else if ((flags & BackPatchInfo::FLAG_LOAD) && (flags & BackPatchInfo::FLAG_FLOAT))
    {
      m_float_emit.LDR(BackPatchInfo::GetFlagSize(flags), EncodeRegToDouble(RS), MEM_REG, addr);

      ByteswapAfterLoad(this, &m_float_emit, EncodeRegToDouble(RS), EncodeRegToDouble(RS), flags,
                        true, false);
    }
    else if (flags & BackPatchInfo::FLAG_STORE)
    {
      ARM64Reg temp = ARM64Reg::W0;
      temp = ByteswapBeforeStore(this, &m_float_emit, temp, RS, flags, true);

      if (flags & BackPatchInfo::FLAG_SIZE_32)
        STR(temp, MEM_REG, addr);
      else if (flags & BackPatchInfo::FLAG_SIZE_16)
        STRH(temp, MEM_REG, addr);
      else
        STRB(temp, MEM_REG, addr);
    }
    else if (flags & BackPatchInfo::FLAG_ZERO_256)
    {
      // This literally only stores 32bytes of zeros to the target address
      ADD(addr, addr, MEM_REG);
      STP(IndexType::Signed, ARM64Reg::ZR, ARM64Reg::ZR, addr, 0);
      STP(IndexType::Signed, ARM64Reg::ZR, ARM64Reg::ZR, addr, 16);
    }
    else
    {
      if (flags & BackPatchInfo::FLAG_SIZE_32)
        LDR(RS, MEM_REG, addr);
      else if (flags & BackPatchInfo::FLAG_SIZE_16)
        LDRH(RS, MEM_REG, addr);
      else if (flags & BackPatchInfo::FLAG_SIZE_8)
        LDRB(RS, MEM_REG, addr);

      ByteswapAfterLoad(this, &m_float_emit, RS, RS, flags, true, false);
    }
  }
  const u8* fastmem_end = GetCodePtr();

  if (!fastmem || do_farcode)
  {
    if (fastmem && do_farcode)
    {
      in_far_code = true;
      SwitchToFarCode();

      if (!emitting_routine)
      {
        FastmemArea* fastmem_area = &m_fault_to_handler[fastmem_end];
        fastmem_area->fastmem_code = fastmem_start;
        fastmem_area->slowmem_code = GetCodePtr();
      }
    }

    if (slowmem_fixup)
      SetJumpTarget(*slowmem_fixup);

    ABI_PushRegisters(gprs_to_push);
    m_float_emit.ABI_PushRegisters(fprs_to_push, ARM64Reg::X30);

    if (flags & BackPatchInfo::FLAG_STORE)
    {
      const u32 access_size = BackPatchInfo::GetFlagSize(flags);
      ARM64Reg src_reg = RS;
      const ARM64Reg dst_reg = access_size == 64 ? ARM64Reg::X0 : ARM64Reg::W0;

      if (flags & BackPatchInfo::FLAG_FLOAT)
      {
        if (access_size == 64)
          m_float_emit.FMOV(dst_reg, EncodeRegToDouble(RS));
        else
          m_float_emit.FMOV(dst_reg, EncodeRegToSingle(RS));

        src_reg = dst_reg;
      }
      
      if (flags & BackPatchInfo::FLAG_PAIR)
      {
        // Compensate for the Write_ functions swapping the whole write instead of each pair
        SwapPairs(this, dst_reg, src_reg, flags);
        src_reg = dst_reg;
      }

      if (dst_reg != src_reg)
        MOV(dst_reg, src_reg);

      if (access_size == 64)
        MOVP2R(ARM64Reg::X8, &PowerPC::Write_U64);
      else if (access_size == 32)
        MOVP2R(ARM64Reg::X8, &PowerPC::Write_U32);
      else if (access_size == 16)
        MOVP2R(ARM64Reg::X8, &PowerPC::Write_U16);
      else
        MOVP2R(ARM64Reg::X8, &PowerPC::Write_U8);

      BLR(ARM64Reg::X8);
    }
    else if (flags & BackPatchInfo::FLAG_ZERO_256)
    {
      MOVP2R(ARM64Reg::X8, &PowerPC::ClearCacheLine);
      BLR(ARM64Reg::X8);
    }
    else
    {
      const u32 access_size = BackPatchInfo::GetFlagSize(flags);

      if (access_size == 64)
        MOVP2R(ARM64Reg::X8, &PowerPC::Read_U64);
      else if (access_size == 32)
        MOVP2R(ARM64Reg::X8, &PowerPC::Read_U32);
      else if (access_size == 16)
        MOVP2R(ARM64Reg::X8, &PowerPC::Read_U16);
      else
        MOVP2R(ARM64Reg::X8, &PowerPC::Read_U8);

      BLR(ARM64Reg::X8);

      ARM64Reg src_reg = access_size == 64 ? ARM64Reg::X0 : ARM64Reg::W0;

      if (flags & BackPatchInfo::FLAG_PAIR)
      {
        // Compensate for the Read_ functions swapping the whole read instead of each pair
        const ARM64Reg dst_reg = flags & BackPatchInfo::FLAG_FLOAT ? src_reg : RS;
        SwapPairs(this, dst_reg, src_reg, flags);
        src_reg = dst_reg;
      }

      if (flags & BackPatchInfo::FLAG_FLOAT)
      {
        if (access_size == 64)
          m_float_emit.FMOV(EncodeRegToDouble(RS), src_reg);
        else
          m_float_emit.FMOV(EncodeRegToSingle(RS), src_reg);

        src_reg = RS;
      }

      ByteswapAfterLoad(this, &m_float_emit, RS, src_reg, flags, false, false);
    }

    m_float_emit.ABI_PopRegisters(fprs_to_push, ARM64Reg::X30);
    ABI_PopRegisters(gprs_to_push);
  }

  if (in_far_code)
  {
    if (emitting_routine)
    {
      FixupBranch done = B();
      SwitchToNearCode();
      SetJumpTarget(done);
    }
    else
    {
      RET(ARM64Reg::X30);
      SwitchToNearCode();
    }
  }
}

bool JitArm64::HandleFastmemFault(uintptr_t access_address, SContext* ctx)
{
  if (!(access_address >= (uintptr_t)Memory::physical_base &&
        access_address < (uintptr_t)Memory::physical_base + 0x100010000) &&
      !(access_address >= (uintptr_t)Memory::logical_base &&
        access_address < (uintptr_t)Memory::logical_base + 0x100010000))
  {
    ERROR_LOG(DYNA_REC,
              "Exception handler - access below memory space. PC: 0x%016llx 0x%016lx < 0x%016lx",
              ctx->CTX_PC, access_address, (uintptr_t)Memory::physical_base);
    return false;
  }

  const u8* pc = reinterpret_cast<const u8*>(ctx->CTX_PC);
  auto slow_handler_iter = m_fault_to_handler.upper_bound(pc);

  // no fastmem area found
  if (slow_handler_iter == m_fault_to_handler.end())
    return false;

  const u8* fastmem_area_start = slow_handler_iter->second.fastmem_code;
  const u8* fastmem_area_end = slow_handler_iter->first;

  // no overlapping fastmem area found
  if (pc < fastmem_area_start)
    return false;

  ARM64XEmitter emitter(const_cast<u8*>(fastmem_area_start), const_cast<u8*>(fastmem_area_end));

  emitter.BL(slow_handler_iter->second.slowmem_code);

  while (emitter.GetCodePtr() < fastmem_area_end)
    emitter.HINT(HINT_NOP);

  m_fault_to_handler.erase(slow_handler_iter);

  emitter.FlushIcache();
  ctx->CTX_PC = reinterpret_cast<std::uintptr_t>(fastmem_area_start);
  return true;
}
