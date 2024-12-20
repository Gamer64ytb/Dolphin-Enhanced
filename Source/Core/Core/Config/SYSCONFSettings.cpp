// Copyright 2017 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "Core/Config/SYSCONFSettings.h"

namespace Config
{
// SYSCONF.IPL

const ConfigInfo<bool> SYSCONF_SCREENSAVER{{System::SYSCONF, "IPL", "SSV"}, false};
const ConfigInfo<u32> SYSCONF_LANGUAGE{{System::SYSCONF, "IPL", "LNG"}, 0x00};
const ConfigInfo<u32> SYSCONF_COUNTRY{{System::SYSCONF, "IPL", "SADR"}, 0x6c};
const ConfigInfo<bool> SYSCONF_WIDESCREEN{{System::SYSCONF, "IPL", "AR"}, true};
const ConfigInfo<bool> SYSCONF_PROGRESSIVE_SCAN{{System::SYSCONF, "IPL", "PGS"}, true};
const ConfigInfo<bool> SYSCONF_PAL60{{System::SYSCONF, "IPL", "E60"}, true};

// SYSCONF.BT

const ConfigInfo<u32> SYSCONF_SENSOR_BAR_POSITION{{System::SYSCONF, "BT", "BAR"}, 0x01};
const ConfigInfo<u32> SYSCONF_SENSOR_BAR_SENSITIVITY{{System::SYSCONF, "BT", "SENS"}, 0x03};
const ConfigInfo<u32> SYSCONF_SPEAKER_VOLUME{{System::SYSCONF, "BT", "SPKV"}, 0x58};
const ConfigInfo<bool> SYSCONF_WIIMOTE_MOTOR{{System::SYSCONF, "BT", "MOT"}, true};

const std::array<SYSCONFSetting, 10> SYSCONF_SETTINGS{
    {{SYSCONF_SCREENSAVER, SysConf::Entry::Type::Byte},
     {SYSCONF_LANGUAGE, SysConf::Entry::Type::Byte},
     {SYSCONF_COUNTRY, SysConf::Entry::Type::BigArray},
     {SYSCONF_WIDESCREEN, SysConf::Entry::Type::Byte},
     {SYSCONF_PROGRESSIVE_SCAN, SysConf::Entry::Type::Byte},
     {SYSCONF_PAL60, SysConf::Entry::Type::Byte},
     {SYSCONF_SENSOR_BAR_POSITION, SysConf::Entry::Type::Byte},
     {SYSCONF_SENSOR_BAR_SENSITIVITY, SysConf::Entry::Type::Long},
     {SYSCONF_SPEAKER_VOLUME, SysConf::Entry::Type::Byte},
     {SYSCONF_WIIMOTE_MOTOR, SysConf::Entry::Type::Byte}}};
}  // namespace Config
