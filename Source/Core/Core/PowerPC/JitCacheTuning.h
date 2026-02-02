#pragma once

#include <cstdint>

namespace JitCacheTuning
{
// Initialize tuning (no-op for now, provided for future extension)
void Initialize();

// Call this instead of JitInterface::ClearCache() to avoid frequent clearing.
void ClearCacheThrottled();

// Configure the cooldown between actual ClearCache() calls (milliseconds).
void SetClearCacheCooldownMS(uint32_t ms);
uint32_t GetClearCacheCooldownMS();
} // namespace JitCacheTuning
