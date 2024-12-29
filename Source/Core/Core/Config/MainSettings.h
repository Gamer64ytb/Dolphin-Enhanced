// Copyright 2017 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#pragma once

#include <string>

#include "Common/Config/Config.h"

namespace PowerPC
{
enum class CPUCore;
}

namespace Config
{
// Main.Core

extern const ConfigInfo<bool> MAIN_SKIP_IPL;
extern const ConfigInfo<bool> MAIN_LOAD_IPL_DUMP;
extern const ConfigInfo<PowerPC::CPUCore> MAIN_CPU_CORE;
extern const ConfigInfo<bool> MAIN_JIT_FOLLOW_BRANCH;
extern const ConfigInfo<bool> MAIN_FASTMEM;
// Should really be in the DSP section, but we're kind of stuck with bad decisions made in the past.
extern const ConfigInfo<bool> MAIN_DSP_HLE;
extern const ConfigInfo<int> MAIN_TIMING_VARIANCE;
extern const ConfigInfo<bool> MAIN_CPU_THREAD;
extern const ConfigInfo<bool> MAIN_SYNC_ON_SKIP_IDLE;
extern const ConfigInfo<std::string> MAIN_DEFAULT_ISO;
extern const ConfigInfo<bool> MAIN_ENABLE_CHEATS;
extern const ConfigInfo<int> MAIN_GC_LANGUAGE;
extern const ConfigInfo<bool> MAIN_OVERRIDE_REGION_SETTINGS;
extern const ConfigInfo<bool> MAIN_DPL2_DECODER;
extern const ConfigInfo<int> MAIN_AUDIO_LATENCY;
extern const ConfigInfo<bool> MAIN_AUDIO_STRETCH;
extern const ConfigInfo<int> MAIN_AUDIO_STRETCH_LATENCY;
extern const ConfigInfo<std::string> MAIN_MEMCARD_A_PATH;
extern const ConfigInfo<std::string> MAIN_MEMCARD_B_PATH;
extern const ConfigInfo<std::string> MAIN_AGP_CART_A_PATH;
extern const ConfigInfo<std::string> MAIN_AGP_CART_B_PATH;
extern const ConfigInfo<std::string> MAIN_GCI_FOLDER_A_PATH_OVERRIDE;
extern const ConfigInfo<std::string> MAIN_GCI_FOLDER_B_PATH_OVERRIDE;
extern const ConfigInfo<bool> MAIN_CODE_SYNC_OVERRIDE;
extern const ConfigInfo<bool> MAIN_GCI_FOLDER_CURRENT_GAME_ONLY;
extern const ConfigInfo<int> MAIN_SLOT_A;
extern const ConfigInfo<int> MAIN_SLOT_B;
extern const ConfigInfo<int> MAIN_SERIAL_PORT_1;
extern const ConfigInfo<std::string> MAIN_BBA_MAC;
ConfigInfo<u32> GetInfoForSIDevice(u32 channel);
ConfigInfo<bool> GetInfoForAdapterRumble(u32 channel);
ConfigInfo<bool> GetInfoForSimulateKonga(u32 channel);
extern const ConfigInfo<bool> MAIN_WII_SD_CARD;
extern const ConfigInfo<bool> MAIN_WII_SD_CARD_WRITABLE;
extern const ConfigInfo<bool> MAIN_WII_KEYBOARD;
extern const ConfigInfo<bool> MAIN_WIIMOTE_CONTINUOUS_SCANNING;
extern const ConfigInfo<bool> MAIN_WIIMOTE_ENABLE_SPEAKER;
extern const ConfigInfo<bool> MAIN_RUN_COMPARE_SERVER;
extern const ConfigInfo<bool> MAIN_RUN_COMPARE_CLIENT;
extern const ConfigInfo<bool> MAIN_MMU;
extern const ConfigInfo<int> MAIN_BB_DUMP_PORT;
extern const ConfigInfo<bool> MAIN_SYNC_GPU;
extern const ConfigInfo<int> MAIN_SYNC_GPU_MAX_DISTANCE;
extern const ConfigInfo<int> MAIN_SYNC_GPU_MIN_DISTANCE;
extern const ConfigInfo<float> MAIN_SYNC_GPU_OVERCLOCK;
extern const ConfigInfo<bool> MAIN_FAST_DISC_SPEED;
extern const ConfigInfo<bool> MAIN_LOW_DCBZ_HACK;
extern const ConfigInfo<bool> MAIN_FLOAT_EXCEPTIONS;
extern const ConfigInfo<bool> MAIN_DIVIDE_BY_ZERO_EXCEPTIONS;
extern const ConfigInfo<bool> MAIN_FPRF;
extern const ConfigInfo<bool> MAIN_ACCURATE_NANS;
extern const ConfigInfo<float> MAIN_EMULATION_SPEED;
extern const ConfigInfo<float> MAIN_OVERCLOCK;
extern const ConfigInfo<bool> MAIN_OVERCLOCK_ENABLE;
extern const ConfigInfo<bool> MAIN_RAM_OVERRIDE_ENABLE;
extern const ConfigInfo<u32> MAIN_MEM1_SIZE;
extern const ConfigInfo<u32> MAIN_MEM2_SIZE;
// Should really be part of System::GFX, but again, we're stuck with past mistakes.
extern const ConfigInfo<std::string> MAIN_GFX_BACKEND;
extern const ConfigInfo<std::string> MAIN_GPU_DETERMINISM_MODE;
extern const ConfigInfo<std::string> MAIN_PERF_MAP_DIR;
extern const ConfigInfo<bool> MAIN_CUSTOM_RTC_ENABLE;
extern const ConfigInfo<u32> MAIN_CUSTOM_RTC_VALUE;
extern const ConfigInfo<bool> MAIN_ENABLE_SIGNATURE_CHECKS;
extern const ConfigInfo<bool> MAIN_REDUCE_POLLING_RATE;
extern const ConfigInfo<bool> MAIN_AUTO_DISC_CHANGE;

// Main.DSP

extern const ConfigInfo<bool> MAIN_DSP_CAPTURE_LOG;
extern const ConfigInfo<bool> MAIN_DSP_JIT;
extern const ConfigInfo<bool> MAIN_DUMP_AUDIO;
extern const ConfigInfo<bool> MAIN_DUMP_AUDIO_SILENT;
extern const ConfigInfo<bool> MAIN_DUMP_UCODE;
extern const ConfigInfo<std::string> MAIN_AUDIO_BACKEND;
extern const ConfigInfo<int> MAIN_AUDIO_VOLUME;

// Main.Display

extern const ConfigInfo<std::string> MAIN_FULLSCREEN_DISPLAY_RES;
extern const ConfigInfo<bool> MAIN_FULLSCREEN;
extern const ConfigInfo<bool> MAIN_RENDER_TO_MAIN;
extern const ConfigInfo<int> MAIN_RENDER_WINDOW_XPOS;
extern const ConfigInfo<int> MAIN_RENDER_WINDOW_YPOS;
extern const ConfigInfo<int> MAIN_RENDER_WINDOW_WIDTH;
extern const ConfigInfo<int> MAIN_RENDER_WINDOW_HEIGHT;
extern const ConfigInfo<bool> MAIN_RENDER_WINDOW_AUTOSIZE;
extern const ConfigInfo<bool> MAIN_KEEP_WINDOW_ON_TOP;
extern const ConfigInfo<bool> MAIN_DISABLE_SCREENSAVER;

// Main.General

extern const ConfigInfo<std::string> MAIN_DUMP_PATH;
extern const ConfigInfo<std::string> MAIN_FS_PATH;
extern const ConfigInfo<std::string> MAIN_SD_PATH;

// Main.Controls

extern const ConfigInfo<int> MAIN_IR_PITCH;
extern const ConfigInfo<int> MAIN_IR_YAW;
extern const ConfigInfo<int> MAIN_IR_VERTICAL_OFFSET;

// Main.Interface

extern const ConfigInfo<std::string> MAIN_SYSTEM_BACK_BIND;

}  // namespace Config
