// Copyright 2015 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#pragma once

#include "Common/Arm64Emitter.h"
#include "Common/Common.h"

#include "Core/HW/MMIO.h"

void ByteswapAfterLoad(Arm64Gen::ARM64XEmitter* emit, Arm64Gen::ARM64Reg dst_reg,
                       Arm64Gen::ARM64Reg src_reg, u32 flags, bool is_reversed, bool is_extended);

Arm64Gen::ARM64Reg ByteswapBeforeStore(Arm64Gen::ARM64XEmitter* emit, Arm64Gen::ARM64Reg tmp_reg,
                                       Arm64Gen::ARM64Reg src_reg, u32 flags, bool want_reversed);

void MMIOLoadToReg(MMIO::Mapping* mmio, Arm64Gen::ARM64XEmitter* emit, BitSet32 gprs_in_use,
                   BitSet32 fprs_in_use, Arm64Gen::ARM64Reg dst_reg, u32 address, u32 flags);

void MMIOWriteRegToAddr(MMIO::Mapping* mmio, Arm64Gen::ARM64XEmitter* emit, BitSet32 gprs_in_use,
                        BitSet32 fprs_in_use, Arm64Gen::ARM64Reg src_reg, u32 address, u32 flags);
