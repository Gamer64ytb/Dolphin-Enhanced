// Copyright 2016 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "Core/PowerPC/Jit64Common/BlockCache.h"

#include "Common/CommonTypes.h"
#include "Common/x64Emitter.h"
#include "Core/PowerPC/JitCommon/JitBase.h"

JitBlockCache::JitBlockCache(JitBase& jit) : JitBaseBlockCache{jit}
{
}

void JitBlockCache::WriteLinkBlock(const JitBlock::LinkData& source, const JitBlock* dest)
{
  // Do not skip breakpoint check if debugging.
  const u8* dispatcher = SConfig::GetInstance().bEnableDebugging ?
                             m_jit.GetAsmRoutines()->dispatcher :
                             m_jit.GetAsmRoutines()->dispatcher_no_check;

  u8* location = source.exitPtrs;
  const u8* address = dest ? dest->checkedEntry : dispatcher;
  if (source.call)
  {
    Gen::XEmitter emit(location, location + 5);
    emit.CALL(address);
  }
  else
  {
    // If we're going to link with the next block, there is no need
    // to emit JMP. So just NOP out the gap to the next block.
    // Support up to 3 additional bytes because of alignment.
    s64 offset = address - location;
    if (offset > 0 && offset <= 5 + 3)
    {
      Gen::XEmitter emit(location, location + offset);
      emit.NOP(offset);
    }
    else
    {
      Gen::XEmitter emit(location, location + 5);
      emit.JMP(address, true);
    }
  }
}

void JitBlockCache::WriteDestroyBlock(const JitBlock& block)
{
  // Only clear the entry points as we might still be within this block.
  Gen::XEmitter emit(block.checkedEntry, block.checkedEntry + 1);
  emit.INT3();
  Gen::XEmitter emit2(block.normalEntry, block.normalEntry + 1);
  emit2.INT3();
}

void JitBlockCache::Init()
{
  JitBaseBlockCache::Init();
  ClearRangesToFree();
}

void JitBlockCache::DestroyBlock(JitBlock& block)
{
  JitBaseBlockCache::DestroyBlock(block);

  if (block.near_begin != block.near_end)
    m_ranges_to_free_on_next_codegen_near.emplace_back(block.near_begin, block.near_end);
  if (block.far_begin != block.far_end)
    m_ranges_to_free_on_next_codegen_far.emplace_back(block.far_begin, block.far_end);
}

const std::vector<std::pair<u8*, u8*>>& JitBlockCache::GetRangesToFreeNear() const
{
  return m_ranges_to_free_on_next_codegen_near;
}

const std::vector<std::pair<u8*, u8*>>& JitBlockCache::GetRangesToFreeFar() const
{
  return m_ranges_to_free_on_next_codegen_far;
}

void JitBlockCache::ClearRangesToFree()
{
  m_ranges_to_free_on_next_codegen_near.clear();
  m_ranges_to_free_on_next_codegen_far.clear();
}
