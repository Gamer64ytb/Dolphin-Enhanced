package org.dolphinemu.dolphinemu.features.settings.ui

import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.IntSetting
import org.dolphinemu.dolphinemu.features.settings.model.PostProcessing
import org.dolphinemu.dolphinemu.features.settings.model.Settings
import org.dolphinemu.dolphinemu.features.settings.model.StringSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.CheckBoxSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.HeaderSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.InputBindingSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.RumbleBindingSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem
import org.dolphinemu.dolphinemu.features.settings.model.view.SingleChoiceSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.SliderSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.StringSingleChoiceSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.SubmenuSetting
import org.dolphinemu.dolphinemu.features.settings.ui.MenuTag.Companion.getGCPadMenuTag
import org.dolphinemu.dolphinemu.features.settings.ui.MenuTag.Companion.getWiimoteExtensionMenuTag
import org.dolphinemu.dolphinemu.features.settings.ui.MenuTag.Companion.getWiimoteMenuTag
import org.dolphinemu.dolphinemu.features.settings.utils.SettingsFile
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization
import org.dolphinemu.dolphinemu.utils.GpuDriverHelper
import java.io.File
import java.util.Locale


class SettingsFragmentPresenter
    (private val activity: SettingsActivity) {
    private var menuTag: MenuTag? = null
    private var gameId: String? = null
    private var settings: Settings? = null

    private var controllerNumber = 0
    private var controllerType = 0

    fun onCreate(menuTag: MenuTag, gameId: String?, extras: Bundle) {
        this.gameId = gameId
        this.menuTag = menuTag

        if (menuTag.isGCPadMenu || menuTag.isWiimoteExtensionMenu) {
            controllerNumber = menuTag.subType
            controllerType = extras.getInt(SettingsActivity.ARG_CONTROLLER_TYPE)
        } else if (menuTag.isWiimoteMenu) {
            controllerNumber = menuTag.subType
        } else if (
            menuTag == MenuTag.GRAPHICS
            && this.gameId.isNullOrEmpty()
            && GpuDriverHelper.supportsCustomDriverLoading()
        ) {
        activity.gpuDriver =
            GpuDriverHelper.getInstalledDriverMetadata() ?: GpuDriverHelper.getSystemDriverMetadata(
                activity.applicationContext
            )
        }
    }

    /**
     * If the screen is rotated, the Activity will forget the settings map. This fragment
     * won't, though; so rather than have the Activity reload from disk, have the fragment pass
     * the settings map back to the Activity.
     */
    fun onAttach() {
        if (settings != null) {
            activity.setSettings(settings!!)
        }
    }

    fun loadSettingsList(settings: Settings?): ArrayList<SettingsItem>? {
        val sl = ArrayList<SettingsItem>()
        this.settings = settings
        when (menuTag) {
            MenuTag.CONFIG -> addConfigSettings(sl)
            MenuTag.CONFIG_GENERAL -> addGeneralSettings(sl)
            MenuTag.CONFIG_INTERFACE -> addInterfaceSettings(sl)
            MenuTag.CONFIG_GAME_CUBE -> addGameCubeSettings(sl)
            MenuTag.CONFIG_WII -> addWiiSettings(sl)
            MenuTag.GRAPHICS -> addGraphicsSettings(sl)
            MenuTag.GCPAD_TYPE -> addGcPadSettings(sl)
            MenuTag.WIIMOTE -> addWiimoteSettings(sl)
            MenuTag.ENHANCEMENTS -> addEnhanceSettings(sl)
            MenuTag.HACKS -> addHackSettings(sl)
            MenuTag.DEBUG -> addDebugSettings(sl)
            MenuTag.GCPAD_1, MenuTag.GCPAD_2, MenuTag.GCPAD_3, MenuTag.GCPAD_4 -> addGcPadSubSettings(
                sl,
                controllerNumber,
                controllerType
            )

            MenuTag.WIIMOTE_1, MenuTag.WIIMOTE_2, MenuTag.WIIMOTE_3, MenuTag.WIIMOTE_4 -> addWiimoteSubSettings(
                sl,
                controllerNumber
            )

            MenuTag.WIIMOTE_EXTENSION_1, MenuTag.WIIMOTE_EXTENSION_2, MenuTag.WIIMOTE_EXTENSION_3, MenuTag.WIIMOTE_EXTENSION_4 -> addExtensionTypeSettings(
                sl,
                controllerNumber,
                controllerType
            )

            else -> return null
        }
        return sl
    }

    private fun addConfigSettings(sl: ArrayList<SettingsItem>) {
        sl.add(SubmenuSetting(null, null, R.string.general_submenu, 0, MenuTag.CONFIG_GENERAL))
        sl.add(
            SubmenuSetting(
                null,
                null,
                R.string.grid_menu_graphics_settings,
                0,
                MenuTag.GRAPHICS
            )
        )
        sl.add(SubmenuSetting(null, null, R.string.enhancements_submenu, 0, MenuTag.ENHANCEMENTS))
        sl.add(SubmenuSetting(null, null, R.string.hacks_submenu, 0, MenuTag.HACKS))
        sl.add(SubmenuSetting(null, null, R.string.interface_submenu, 0, MenuTag.CONFIG_INTERFACE))
        sl.add(SubmenuSetting(null, null, R.string.gamecube_submenu, 0, MenuTag.CONFIG_GAME_CUBE))
        sl.add(SubmenuSetting(null, null, R.string.wii_submenu, 0, MenuTag.CONFIG_WII))
        sl.add(SubmenuSetting(null, null, R.string.debug_submenu, 0, MenuTag.DEBUG))
    }

    private fun addGeneralSettings(sl: ArrayList<SettingsItem>) {
        val coreSection = settings!!.getSection(Settings.SECTION_INI_CORE)
        val hwSection = settings!!.getSection(Settings.SECTION_GFX_HARDWARE)
        val cpuCore = coreSection.getSetting(SettingsFile.KEY_CPU_CORE)
        val dualCore = coreSection.getSetting(SettingsFile.KEY_DUAL_CORE)
        val overclockEnable = coreSection.getSetting(SettingsFile.KEY_OVERCLOCK_ENABLE)
        val overclock = coreSection.getSetting(SettingsFile.KEY_OVERCLOCK_PERCENT)
        val speedLimit = coreSection.getSetting(SettingsFile.KEY_SPEED_LIMIT)
        val syncOnSkipIdle = coreSection.getSetting(SettingsFile.KEY_SYNC_ON_SKIP_IDLE)
        val mmu = coreSection.getSetting(SettingsFile.KEY_MMU)
        val fastDiscSpeed = coreSection.getSetting(SettingsFile.KEY_FAST_DISC_SPEED)
        val followBranch = coreSection.getSetting(SettingsFile.KEY_JIT_FOLLOW_BRANCH)
        val vsync = hwSection.getSetting(SettingsFile.KEY_VSYNC)
        val overrideRegionSettings =
            coreSection.getSetting(SettingsFile.KEY_OVERRIDE_REGION_SETTINGS)
        val autoDiscChange = coreSection.getSetting(SettingsFile.KEY_AUTO_DISC_CHANGE)
        val audioStretch = coreSection.getSetting(SettingsFile.KEY_AUDIO_STRETCH)
        val stretchLatency = coreSection.getSetting(SettingsFile.KEY_AUDIO_STRETCH_MAX_LATENCY)
        val audioBackend = settings!!.getSection(Settings.SECTION_INI_DSP)
            .getSetting(SettingsFile.KEY_AUDIO_BACKEND)
        val enableCheats = coreSection.getSetting(SettingsFile.KEY_ENABLE_CHEATS)
        val ramOverrideEnable = coreSection.getSetting(SettingsFile.KEY_RAM_OVERRIDE_ENABLE)
        val mem1Size = coreSection.getSetting(SettingsFile.KEY_MEM1_SIZE)
        val mem2Size = coreSection.getSetting(SettingsFile.KEY_MEM2_SIZE)

        // TODO: Having different emuCoresEntries/emuCoresValues for each architecture is annoying.
        // The proper solution would be to have one emuCoresEntries and one emuCoresValues
        // and exclude the values that aren't present in PowerPC::AvailableCPUCores().
        val defaultCpuCore = NativeLibrary.DefaultCPUCore()
        val emuCoresEntries: Int
        val emuCoresValues: Int
        if (defaultCpuCore == 4)  // AArch64
        {
            emuCoresEntries = R.array.emuCoresEntriesARM64
            emuCoresValues = R.array.emuCoresValuesARM64
        } else {
            emuCoresEntries = R.array.emuCoresEntriesGeneric
            emuCoresValues = R.array.emuCoresValuesGeneric
        }
        sl.add(
            SingleChoiceSetting(
                SettingsFile.KEY_CPU_CORE, Settings.SECTION_INI_CORE,
                R.string.cpu_core, 0, emuCoresEntries, emuCoresValues, defaultCpuCore, cpuCore
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_DUAL_CORE, Settings.SECTION_INI_CORE,
                R.string.dual_core, R.string.dual_core_description, true, dualCore
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_OVERCLOCK_ENABLE, Settings.SECTION_INI_CORE,
                R.string.overclock_enable, R.string.overclock_enable_description, false,
                overclockEnable
            )
        )
        sl.add(
            SliderSetting(
                SettingsFile.KEY_OVERCLOCK_PERCENT, Settings.SECTION_INI_CORE,
                R.string.overclock_title, R.string.overclock_title_description, 400, "%", 100,
                overclock
            )
        )
        sl.add(
            SliderSetting(
                SettingsFile.KEY_SPEED_LIMIT, Settings.SECTION_INI_CORE,
                R.string.speed_limit, 0, 200, "%", 100, speedLimit
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_SYNC_ON_SKIP_IDLE, Settings.SECTION_INI_CORE,
                R.string.sync_on_skip_idle, R.string.sync_on_skip_idle_description, true,
                syncOnSkipIdle
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_MMU, Settings.SECTION_INI_CORE,
                R.string.mmu_enable, R.string.mmu_enable_description, false, mmu
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_FAST_DISC_SPEED, Settings.SECTION_INI_CORE,
                R.string.fast_disc_speed, R.string.fast_disc_speed_description, false, fastDiscSpeed
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_JIT_FOLLOW_BRANCH, Settings.SECTION_INI_CORE,
                R.string.jit_follow_branch, R.string.jit_follow_branch_description, true,
                followBranch
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_OVERRIDE_REGION_SETTINGS, Settings.SECTION_INI_CORE,
                R.string.override_region_settings, 0, false, overrideRegionSettings
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_VSYNC, Settings.SECTION_GFX_HARDWARE,
                R.string.vsync, R.string.vsync_description, false, vsync
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_ENABLE_CHEATS, Settings.SECTION_INI_CORE,
                R.string.enable_cheats, R.string.enable_cheats_description, false, enableCheats
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_AUTO_DISC_CHANGE, Settings.SECTION_INI_CORE,
                R.string.auto_disc_change, 0, false, autoDiscChange
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_AUDIO_STRETCH, Settings.SECTION_INI_CORE,
                R.string.audio_stretch, R.string.audio_stretch_description, false, audioStretch
            )
        )
        sl.add(
            SliderSetting(
                SettingsFile.KEY_AUDIO_STRETCH_MAX_LATENCY,
                Settings.SECTION_INI_CORE,
                R.string.audio_stretch_max_latency,
                R.string.audio_stretch_max_latency_description,
                300,
                "",
                80,
                stretchLatency
            )
        )

        val defaultAudioBackend = NativeLibrary.DefaultAudioBackend()
        val audioListEntries = NativeLibrary.GetAudioBackendList()
        val audioListValues = arrayOfNulls<String>(audioListEntries.size)
        System.arraycopy(audioListEntries, 0, audioListValues, 0, audioListEntries.size)
        sl.add(
            StringSingleChoiceSetting(
                SettingsFile.KEY_AUDIO_BACKEND, Settings.SECTION_INI_DSP,
                R.string.audio_backend, 0, audioListEntries,
                audioListValues, defaultAudioBackend, audioBackend
            )
        )
        sl.add(HeaderSetting(null, null, R.string.memory_override, 0))
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_RAM_OVERRIDE_ENABLE, Settings.SECTION_INI_CORE,
                R.string.enable_memory_size_override, R.string.enable_memory_size_override_description,
                false, ramOverrideEnable
            )
        )
        sl.add(
            SliderSetting(
                SettingsFile.KEY_MEM1_SIZE,
                Settings.SECTION_INI_CORE,
                R.string.main_mem1_size,
                0,
                64,
                "MB",
                24,
                mem1Size
            )
        )
        sl.add(
            SliderSetting(
                SettingsFile.KEY_MEM2_SIZE,
                Settings.SECTION_INI_CORE,
                R.string.main_mem2_size,
                0,
                128,
                "MB",
                64,
                mem2Size
            )
        )
    }

    private fun addInterfaceSettings(sl: ArrayList<SettingsItem>) {
        val uiSection = settings!!.getSection(Settings.SECTION_INI_INTERFACE)
        val expandToCutoutArea = uiSection.getSetting(SettingsFile.KEY_EXPAND_TO_CUTOUT_AREA)
        val design = uiSection.getSetting(SettingsFile.KEY_DESIGN)
        val usePanicHandlers = uiSection.getSetting(SettingsFile.KEY_USE_PANIC_HANDLERS)
        val onScreenDisplayMessages = uiSection.getSetting(SettingsFile.KEY_OSD_MESSAGES)
        val useBuiltinTitleDatabase = uiSection.getSetting(SettingsFile.KEY_BUILTIN_TITLE_DATABASE)
        val systemBack = uiSection.getSetting(SettingsFile.KEY_SYSTEM_BACK)
        val gcTheme = uiSection.getSetting(SettingsFile.KEY_GC_THEME)
        val dpadTheme = uiSection.getSetting(SettingsFile.KEY_DPAD_THEME)
        val joystickTheme = uiSection.getSetting(SettingsFile.KEY_JOYSTICK_THEME)
        val wiimoteTheme = uiSection.getSetting(SettingsFile.KEY_WIIMOTE_THEME)
        val classicTheme = uiSection.getSetting(SettingsFile.KEY_CLASSIC_THEME)

        // Only android 9+ supports this feature.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            sl.add(
                CheckBoxSetting(
                    SettingsFile.KEY_EXPAND_TO_CUTOUT_AREA,
                    Settings.SECTION_INI_INTERFACE,
                    R.string.expand_to_cutout_area,
                    R.string.expand_to_cutout_area_description,
                    false,
                    expandToCutoutArea
                )
            )
        }

        if (gameId!!.isEmpty()) {
            sl.add(
                SingleChoiceSetting(
                    SettingsFile.KEY_DESIGN, Settings.SECTION_INI_INTERFACE,
                    R.string.design, 0, R.array.designNames, R.array.designValues, 2, design
                )
            )
        }

        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_USE_PANIC_HANDLERS, Settings.SECTION_INI_INTERFACE,
                R.string.panic_handlers, R.string.panic_handlers_description, true, usePanicHandlers
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_OSD_MESSAGES, Settings.SECTION_INI_INTERFACE,
                R.string.osd_messages, R.string.osd_messages_description, true,
                onScreenDisplayMessages
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_BUILTIN_TITLE_DATABASE, Settings.SECTION_INI_INTERFACE,
                R.string.use_builtin_title_database, 0, true, useBuiltinTitleDatabase
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_SYSTEM_BACK, Settings.SECTION_INI_INTERFACE,
                R.string.system_back, systemBack
            )
        )

        // themes
        if (gameId.isNullOrEmpty()) {
            val gcStringValues = getGameCubeThemeValues()
            val dpadStringValues = getDpadThemeValues()
            val joystickStringValues = getJoystickThemeValues()
            val wiimoteStringValues = getWiimoteThemeValues()
            val classicStringValues = getClassicThemeValues()
            val gcStringEntries = getSettingEntries(gcStringValues)
            val dpadStringEntries = getSettingEntries(dpadStringValues)
            val joystickStringEntries = getSettingEntries(joystickStringValues)
            val wiimoteStringEntries = getSettingEntries(wiimoteStringValues)
            val classicStringEntries = getSettingEntries(classicStringValues)

            sl.add(HeaderSetting(null, null, R.string.themes, 0))

            sl.add(
                StringSingleChoiceSetting(
                    SettingsFile.KEY_GC_THEME, Settings.SECTION_INI_INTERFACE,
                    R.string.gc_theme, 0, gcStringEntries, gcStringValues,
                    "gcDefault", gcTheme
                )
            )
            sl.add(
                StringSingleChoiceSetting(
                    SettingsFile.KEY_DPAD_THEME, Settings.SECTION_INI_INTERFACE,
                    R.string.dpad_theme, 0, dpadStringEntries, dpadStringValues,
                    "dpadDefault", dpadTheme
                )
            )
            sl.add(
                StringSingleChoiceSetting(
                    SettingsFile.KEY_JOYSTICK_THEME, Settings.SECTION_INI_INTERFACE,
                    R.string.joystick_theme, 0, joystickStringEntries, joystickStringValues,
                    "joystickDefault", joystickTheme
                )
            )
            sl.add(
                StringSingleChoiceSetting(
                    SettingsFile.KEY_WIIMOTE_THEME, Settings.SECTION_INI_INTERFACE,
                    R.string.wiimote_theme, 0, wiimoteStringEntries, wiimoteStringValues,
                    "wiimoteDefault", wiimoteTheme
                )
            )
            sl.add(
                StringSingleChoiceSetting(
                    SettingsFile.KEY_CLASSIC_THEME, Settings.SECTION_INI_INTERFACE,
                    R.string.classic_theme, 0, classicStringEntries, classicStringValues,
                    "classicDefault", classicTheme
                )
            )
        }
    }

    private fun addGameCubeSettings(sl: ArrayList<SettingsItem>) {
        val coreSection = settings!!.getSection(Settings.SECTION_INI_CORE)
        val systemLanguage = coreSection.getSetting(SettingsFile.KEY_GAME_CUBE_LANGUAGE)
        val slotADevice = coreSection.getSetting(SettingsFile.KEY_SLOT_A_DEVICE)
        val slotBDevice = coreSection.getSetting(SettingsFile.KEY_SLOT_B_DEVICE)
        val serialDevice = coreSection.getSetting(SettingsFile.KEY_SERIAL_PORT_1)

        sl.add(
            SingleChoiceSetting(
                SettingsFile.KEY_GAME_CUBE_LANGUAGE, Settings.SECTION_INI_CORE,
                R.string.gamecube_system_language, 0, R.array.gameCubeSystemLanguageEntries,
                R.array.gameCubeSystemLanguageValues, 0, systemLanguage
            )
        )
        sl.add(
            SingleChoiceSetting(
                SettingsFile.KEY_SLOT_A_DEVICE, Settings.SECTION_INI_CORE,
                R.string.slot_a_device, 0, R.array.slotDeviceEntries, R.array.slotDeviceValues, 8,
                slotADevice
            )
        )
        sl.add(
            SingleChoiceSetting(
                SettingsFile.KEY_SLOT_B_DEVICE, Settings.SECTION_INI_CORE,
                R.string.slot_b_device, 0, R.array.slotDeviceEntries, R.array.slotDeviceValues, 255,
                slotBDevice
            )
        )
        sl.add(
            SingleChoiceSetting(
                SettingsFile.KEY_SERIAL_PORT_1,
                Settings.SECTION_INI_CORE,
                R.string.serial_port_1,
                0,
                R.array.serialDeviceEntries,
                R.array.serialDeviceValues,
                255,
                serialDevice
            )
        )
    }

    private fun addWiiSettings(sl: ArrayList<SettingsItem>) {
        val coreSection = settings!!.getSection(Settings.SECTION_INI_CORE)
        val continuousScan = coreSection.getSetting(SettingsFile.KEY_WIIMOTE_SCAN)
        val wiimoteSpeaker = coreSection.getSetting(SettingsFile.KEY_WIIMOTE_SPEAKER)
        val wiiSDCard = coreSection.getSetting(SettingsFile.KEY_WII_SD_CARD)

        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_WIIMOTE_SCAN, Settings.SECTION_INI_CORE,
                R.string.wiimote_scanning, R.string.wiimote_scanning_description, true,
                continuousScan
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_WIIMOTE_SPEAKER, Settings.SECTION_INI_CORE,
                R.string.wiimote_speaker, R.string.wiimote_speaker_description, true, wiimoteSpeaker
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_WII_SD_CARD, Settings.SECTION_INI_CORE,
                R.string.wii_sd_card, R.string.wii_sd_card_description, false, wiiSDCard
            )
        )

        // SYSCONF_SETTINGS
        val sysconfSection = settings!!.getSection(Settings.SECTION_WII_IPL)
        val screensaver = sysconfSection.getSetting(SettingsFile.KEY_SYSCONF_SCREENSAVER)
        val language = sysconfSection.getSetting(SettingsFile.KEY_SYSCONF_LANGUAGE)
        val widescreen = sysconfSection.getSetting(SettingsFile.KEY_SYSCONF_WIDESCREEN)
        val progressiveScan = sysconfSection.getSetting(SettingsFile.KEY_SYSCONF_PROGRESSIVE_SCAN)
        val pal60 = sysconfSection.getSetting(SettingsFile.KEY_SYSCONF_PAL60)

        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_SYSCONF_SCREENSAVER, Settings.SECTION_WII_IPL,
                R.string.sysconf_screensaver, 0, false, screensaver
            )
        )
        sl.add(
            SingleChoiceSetting(
                SettingsFile.KEY_SYSCONF_LANGUAGE, Settings.SECTION_WII_IPL,
                R.string.sysconf_language, 0, R.array.wiiSystemLanguageEntries,
                R.array.wiiSystemLanguageValues, 0, language
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_SYSCONF_WIDESCREEN, Settings.SECTION_WII_IPL,
                R.string.sysconf_widescreen, 0, true, widescreen
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_SYSCONF_PROGRESSIVE_SCAN, Settings.SECTION_WII_IPL,
                R.string.sysconf_progressive_scan, 0, true, progressiveScan
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_SYSCONF_PAL60, Settings.SECTION_WII_IPL,
                R.string.sysconf_pal60, 0, true, pal60
            )
        )
    }

    private fun addGcPadSettings(sl: ArrayList<SettingsItem>) {
        for (i in 0..3) {
            if (TextUtils.isEmpty(gameId)) {
                // TODO This controller_0 + i business is quite the hack. It should work, but only if the definitions are kept together and in order.
                val gcPadSetting = settings!!.getSection(Settings.SECTION_INI_CORE)
                    .getSetting(SettingsFile.KEY_GCPAD_TYPE + i)
                sl.add(
                    SingleChoiceSetting(
                        SettingsFile.KEY_GCPAD_TYPE + i,
                        Settings.SECTION_INI_CORE,
                        R.string.controller_0 + i,
                        0,
                        R.array.gcpadTypeEntries,
                        R.array.gcpadTypeValues,
                        0,
                        gcPadSetting,
                        getGCPadMenuTag(i)
                    )
                )
            } else {
                val gcPadSetting = settings!!.getSection(Settings.SECTION_CONTROLS)
                    .getSetting(SettingsFile.KEY_GCPAD_G_TYPE + i)
                sl.add(
                    SingleChoiceSetting(
                        SettingsFile.KEY_GCPAD_G_TYPE + i,
                        Settings.SECTION_CONTROLS,
                        R.string.controller_0 + i,
                        0,
                        R.array.gcpadTypeEntries,
                        R.array.gcpadTypeValues,
                        0,
                        gcPadSetting,
                        getGCPadMenuTag(i)
                    )
                )
            }
        }
    }

    private fun addWiimoteSettings(sl: ArrayList<SettingsItem>) {
        for (i in 0..3) {
            // TODO This wiimote_0 + i business is quite the hack. It should work, but only if the definitions are kept together and in order.
            if (TextUtils.isEmpty(gameId)) {
                val wiimoteSetting = settings!!.getSection(Settings.SECTION_WIIMOTE + (i + 1))
                    .getSetting(SettingsFile.KEY_WIIMOTE_TYPE)
                sl.add(
                    SingleChoiceSetting(
                        SettingsFile.KEY_WIIMOTE_TYPE,
                        Settings.SECTION_WIIMOTE + (i + 1), R.string.wiimote_4 + i, 0,
                        R.array.wiimoteTypeEntries, R.array.wiimoteTypeValues, 0, wiimoteSetting,
                        getWiimoteMenuTag(i + 4)
                    )
                )
            } else {
                val wiimoteSetting = settings!!.getSection(Settings.SECTION_CONTROLS)
                    .getSetting(SettingsFile.KEY_WIIMOTE_G_TYPE + i)
                sl.add(
                    SingleChoiceSetting(
                        SettingsFile.KEY_WIIMOTE_G_TYPE + i,
                        Settings.SECTION_CONTROLS,
                        R.string.wiimote_4 + i,
                        0,
                        R.array.wiimoteTypeEntries,
                        R.array.wiimoteTypeValues,
                        0,
                        wiimoteSetting,
                        getWiimoteMenuTag(i + 4)
                    )
                )
            }
        }
    }

    private fun addGraphicsSettings(sl: ArrayList<SettingsItem>) {
        val videoBackend =
            IntSetting(
                SettingsFile.KEY_VIDEO_BACKEND_INDEX, Settings.SECTION_INI_CORE,
                videoBackendValue
            )

        val gfxSection = settings!!.getSection(Settings.SECTION_GFX_SETTINGS)
        val showFps = gfxSection.getSetting(SettingsFile.KEY_SHOW_FPS)
        val shaderCompilationMode = gfxSection.getSetting(SettingsFile.KEY_SHADER_COMPILATION_MODE)
        val waitForShaders = gfxSection.getSetting(SettingsFile.KEY_WAIT_FOR_SHADERS)
        val aspectRatio = gfxSection.getSetting(SettingsFile.KEY_ASPECT_RATIO)
        val displayScale = gfxSection.getSetting(SettingsFile.KEY_DISPLAY_SCALE)
        val backendMultithreading = gfxSection.getSetting(SettingsFile.KEY_BACKEND_MULTITHREADING)

        sl.add(
            SingleChoiceSetting(
                SettingsFile.KEY_VIDEO_BACKEND_INDEX, Settings.SECTION_INI_CORE,
                R.string.video_backend, 0, R.array.videoBackendEntries,
                R.array.videoBackendValues, 0, videoBackend
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_SHOW_FPS, Settings.SECTION_GFX_SETTINGS,
                R.string.show_fps, R.string.show_fps_description, false, showFps
            )
        )
        sl.add(
            SingleChoiceSetting(
                SettingsFile.KEY_SHADER_COMPILATION_MODE,
                Settings.SECTION_GFX_SETTINGS, R.string.shader_compilation_mode,
                R.string.shader_compilation_mode_description, R.array.shaderCompilationModeEntries,
                R.array.shaderCompilationModeValues, 0, shaderCompilationMode
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_WAIT_FOR_SHADERS, Settings.SECTION_GFX_SETTINGS,
                R.string.wait_for_shaders, R.string.wait_for_shaders_description, false,
                waitForShaders
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_BACKEND_MULTITHREADING,
                Settings.SECTION_GFX_SETTINGS,
                R.string.backend_multithreading, R.string.backend_multithreading_description, true,
                backendMultithreading
            )
        )
        if (
            activity.gpuDriver != null && gameId.isNullOrEmpty()
            && GpuDriverHelper.supportsCustomDriverLoading()
        ) {
            sl.add(SubmenuSetting(null, null, R.string.gpu_driver_submenu, 0, MenuTag.GPU_DRIVERS))
        }
        sl.add(
            SingleChoiceSetting(
                SettingsFile.KEY_ASPECT_RATIO, Settings.SECTION_GFX_SETTINGS,
                R.string.aspect_ratio, 0, R.array.aspectRatioEntries,
                R.array.aspectRatioValues, 0, aspectRatio
            )
        )
        sl.add(
            SliderSetting(
                SettingsFile.KEY_DISPLAY_SCALE, Settings.SECTION_GFX_SETTINGS,
                R.string.setting_display_scale, 0, 200, "%", 100, displayScale
            )
        )
    }

    private fun addEnhanceSettings(sl: ArrayList<SettingsItem>) {
        val gfxSection = settings!!.getSection(Settings.SECTION_GFX_SETTINGS)
        val enhancementSection = settings!!.getSection(Settings.SECTION_GFX_ENHANCEMENTS)
        val hacksSection = settings!!.getSection(Settings.SECTION_GFX_HACKS)

        val resolution = gfxSection.getSetting(SettingsFile.KEY_INTERNAL_RES)
        val fsaa = gfxSection.getSetting(SettingsFile.KEY_FSAA)
        val anisotropic = enhancementSection.getSetting(SettingsFile.KEY_ANISOTROPY)
        val shader = enhancementSection.getSetting(SettingsFile.KEY_POST_SHADER)
        val hiresTextures = gfxSection.getSetting(SettingsFile.KEY_HIRES_TEXTURES)
        val cacheHiresTextures = gfxSection.getSetting(SettingsFile.KEY_CACHE_HIRES_TEXTURES)
        val efbScaledCopy = hacksSection.getSetting(SettingsFile.KEY_SCALED_EFB)
        val perPixel = gfxSection.getSetting(SettingsFile.KEY_PER_PIXEL)
        val forceFilter = enhancementSection.getSetting(SettingsFile.KEY_FORCE_FILTERING)
        val disableFog = gfxSection.getSetting(SettingsFile.KEY_DISABLE_FOG)
        val disableCopyFilter = enhancementSection.getSetting(SettingsFile.KEY_DISABLE_COPY_FILTER)
        val arbitraryMipmapDetection =
            enhancementSection.getSetting(SettingsFile.KEY_ARBITRARY_MIPMAP_DETECTION)
        val wideScreenHack = gfxSection.getSetting(SettingsFile.KEY_WIDE_SCREEN_HACK)
        val force24BitColor = enhancementSection.getSetting(SettingsFile.KEY_FORCE_24_BIT_COLOR)

        sl.add(
            SliderSetting(
                SettingsFile.KEY_INTERNAL_RES, Settings.SECTION_GFX_SETTINGS,
                R.string.internal_resolution, R.string.internal_resolution_description, 400, "x", 100,
                resolution
            )
        )
        sl.add(
            SingleChoiceSetting(
                SettingsFile.KEY_FSAA,
                Settings.SECTION_GFX_SETTINGS,
                R.string.FSAA,
                R.string.FSAA_description,
                R.array.FSAAEntries,
                R.array.FSAAValues,
                1,
                fsaa
            )
        )
        sl.add(
            SingleChoiceSetting(
                SettingsFile.KEY_ANISOTROPY, Settings.SECTION_GFX_ENHANCEMENTS,
                R.string.anisotropic_filtering, R.string.anisotropic_filtering_description,
                R.array.anisotropicFilteringEntries, R.array.anisotropicFilteringValues, 0,
                anisotropic
            )
        )

        val shaderList = PostProcessing.shaderList
        val shaderListEntries = arrayOfNulls<String>(shaderList.size + 1)
        shaderListEntries[0] = activity.getString(R.string.off)
        System.arraycopy(shaderList, 0, shaderListEntries, 1, shaderList.size)

        val shaderListValues = arrayOfNulls<String>(shaderList.size + 1)
        shaderListValues[0] = ""
        System.arraycopy(shaderList, 0, shaderListValues, 1, shaderList.size)

        sl.add(
            StringSingleChoiceSetting(
                SettingsFile.KEY_POST_SHADER,
                Settings.SECTION_GFX_ENHANCEMENTS, R.string.post_processing_shader,
                0, shaderListEntries, shaderListValues, "",
                shader
            )
        )

        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_HIRES_TEXTURES,
                Settings.SECTION_GFX_SETTINGS,
                R.string.load_custom_texture,
                R.string.load_custom_texture_description,
                false,
                hiresTextures
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_CACHE_HIRES_TEXTURES,
                Settings.SECTION_GFX_SETTINGS,
                R.string.cache_custom_texture,
                R.string.cache_custom_texture_description,
                false,
                cacheHiresTextures
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_SCALED_EFB, Settings.SECTION_GFX_HACKS,
                R.string.scaled_efb_copy, R.string.scaled_efb_copy_description, true, efbScaledCopy
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_PER_PIXEL,
                Settings.SECTION_GFX_SETTINGS,
                R.string.per_pixel_lighting,
                R.string.per_pixel_lighting_description,
                false,
                perPixel
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_FORCE_FILTERING,
                Settings.SECTION_GFX_ENHANCEMENTS,
                R.string.force_texture_filtering,
                R.string.force_texture_filtering_description,
                false,
                forceFilter
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_FORCE_24_BIT_COLOR, Settings.SECTION_GFX_ENHANCEMENTS,
                R.string.force_24bit_color, R.string.force_24bit_color_description, true,
                force24BitColor
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_DISABLE_FOG, Settings.SECTION_GFX_SETTINGS,
                R.string.disable_fog, R.string.disable_fog_description, false, disableFog
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_DISABLE_COPY_FILTER, Settings.SECTION_GFX_ENHANCEMENTS,
                R.string.disable_copy_filter, R.string.disable_copy_filter_description, false,
                disableCopyFilter
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_ARBITRARY_MIPMAP_DETECTION,
                Settings.SECTION_GFX_ENHANCEMENTS, R.string.arbitrary_mipmap_detection,
                R.string.arbitrary_mipmap_detection_description, true, arbitraryMipmapDetection
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_WIDE_SCREEN_HACK, Settings.SECTION_GFX_SETTINGS,
                R.string.wide_screen_hack, R.string.wide_screen_hack_description, false,
                wideScreenHack
            )
        )
    }

    private fun addHackSettings(sl: ArrayList<SettingsItem>) {
        val gfxSection = settings!!.getSection(Settings.SECTION_GFX_SETTINGS)
        val hacksSection = settings!!.getSection(Settings.SECTION_GFX_HACKS)

        val skipEFB = hacksSection.getSetting(SettingsFile.KEY_SKIP_EFB)
        val ignoreFormat = hacksSection.getSetting(SettingsFile.KEY_IGNORE_FORMAT)
        val efbToTexture = hacksSection.getSetting(SettingsFile.KEY_EFB_TEXTURE)
        val deferEfbCopies = hacksSection.getSetting(SettingsFile.KEY_DEFER_EFB_COPIES)
        val deferEfbInvalid = hacksSection.getSetting(SettingsFile.KEY_EFB_DEFER_INVALIDATION)
        val texCacheAccuracy = gfxSection.getSetting(SettingsFile.KEY_TEXCACHE_ACCURACY)
        val gpuTextureDecoding = gfxSection.getSetting(SettingsFile.KEY_GPU_TEXTURE_DECODING)
        val xfbToTexture = hacksSection.getSetting(SettingsFile.KEY_XFB_TEXTURE)
        val immediateXfb = hacksSection.getSetting(SettingsFile.KEY_IMMEDIATE_XFB)
        val skipDuplicateXfbs = hacksSection.getSetting(SettingsFile.KEY_SKIP_DUPLICATE_XFBS)
        val approxLogicOpWithBlending =
            hacksSection.getSetting(SettingsFile.KEY_APPROX_LOGIC_OP_WITH_BLENDING)
        val viSkip = hacksSection.getSetting(SettingsFile.KEY_VI_SKIP)
        val saveTexCacheToState =
            gfxSection.getSetting(SettingsFile.KEY_SAVE_TEXTURE_CACHE_TO_STATE)
        val fastDepth = gfxSection.getSetting(SettingsFile.KEY_FAST_DEPTH)
        val tmemEmu = hacksSection.getSetting(SettingsFile.KEY_TMEM_CACHE_EMULATION)

        sl.add(HeaderSetting(null, null, R.string.embedded_frame_buffer, 0))

        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_SKIP_EFB, Settings.SECTION_GFX_HACKS,
                R.string.skip_efb_access, R.string.skip_efb_access_description, true, skipEFB
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_IGNORE_FORMAT, Settings.SECTION_GFX_HACKS,
                R.string.ignore_format_changes, R.string.ignore_format_changes_description, true,
                ignoreFormat
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_EFB_TEXTURE, Settings.SECTION_GFX_HACKS,
                R.string.efb_copy_method, R.string.efb_copy_method_description, true, efbToTexture
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_DEFER_EFB_COPIES,
                Settings.SECTION_GFX_HACKS,
                R.string.defer_efb_copies,
                R.string.defer_efb_copies_description,
                true,
                deferEfbCopies
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_EFB_DEFER_INVALIDATION,
                Settings.SECTION_GFX_HACKS,
                R.string.efb_defer_invalidation,
                R.string.efb_defer_invalidation_description,
                false,
                deferEfbInvalid
            )
        )

        sl.add(HeaderSetting(null, null, R.string.texture_cache, 0))
        sl.add(
            SingleChoiceSetting(
                SettingsFile.KEY_TEXCACHE_ACCURACY,
                Settings.SECTION_GFX_SETTINGS, R.string.texture_cache_accuracy,
                R.string.texture_cache_accuracy_description, R.array.textureCacheAccuracyEntries,
                R.array.textureCacheAccuracyValues, 128, texCacheAccuracy
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_GPU_TEXTURE_DECODING,
                Settings.SECTION_GFX_SETTINGS,
                R.string.gpu_texture_decoding,
                R.string.gpu_texture_decoding_description,
                false,
                gpuTextureDecoding
            )
        )

        sl.add(HeaderSetting(null, null, R.string.external_frame_buffer, 0))
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_XFB_TEXTURE, Settings.SECTION_GFX_HACKS,
                R.string.xfb_copy_method, R.string.xfb_copy_method_description, true, xfbToTexture
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_IMMEDIATE_XFB, Settings.SECTION_GFX_HACKS,
                R.string.immediate_xfb, R.string.immediate_xfb_description, false, immediateXfb
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_SKIP_DUPLICATE_XFBS, Settings.SECTION_GFX_HACKS,
                R.string.skip_duplicate_xfbs, R.string.skip_duplicate_xfbs_description, true,
                skipDuplicateXfbs
            )
        )

        sl.add(HeaderSetting(null, null, R.string.other, 0))
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_APPROX_LOGIC_OP_WITH_BLENDING,
                Settings.SECTION_GFX_HACKS,
                R.string.approx_logic_op_with_blending,
                R.string.approx_logic_op_with_blending_description,
                false,
                approxLogicOpWithBlending
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_VI_SKIP, Settings.SECTION_GFX_HACKS,
                R.string.vi_skip, R.string.vi_skip_description, false, viSkip
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_SAVE_TEXTURE_CACHE_TO_STATE,
                Settings.SECTION_GFX_SETTINGS,
                R.string.texture_cache_to_state,
                R.string.texture_cache_to_state_description,
                true,
                saveTexCacheToState
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_FAST_DEPTH,
                Settings.SECTION_GFX_SETTINGS,
                R.string.fast_depth_calculation,
                R.string.fast_depth_calculation_description,
                true,
                fastDepth
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_TMEM_CACHE_EMULATION,
                Settings.SECTION_GFX_HACKS,
                R.string.tmem_cache_emulation,
                R.string.tmem_cache_emulation_description,
                true,
                tmemEmu
            )
        )
    }

    private fun addDebugSettings(sl: ArrayList<SettingsItem>) {
        val debugSection = settings!!.getSection(Settings.SECTION_DEBUG)

        val jitOff = debugSection.getSetting(SettingsFile.KEY_DEBUG_JITOFF)
        val jitLoadStoreOff = debugSection.getSetting(SettingsFile.KEY_DEBUG_JITLOADSTOREOFF)
        val jitLoadStoreFloatingPointOff =
            debugSection.getSetting(SettingsFile.KEY_DEBUG_JITLOADSTOREFLOATINGPOINTOFF)
        val jitLoadStorePairedOff =
            debugSection.getSetting(SettingsFile.KEY_DEBUG_JITLOADSTOREPAIREDOFF)
        val jitFloatingPointOff =
            debugSection.getSetting(SettingsFile.KEY_DEBUG_JITFLOATINGPOINTOFF)
        val jitIntegerOff = debugSection.getSetting(SettingsFile.KEY_DEBUG_JITINTEGEROFF)
        val jitPairedOff = debugSection.getSetting(SettingsFile.KEY_DEBUG_JITPAIREDOFF)
        val jitSystemRegistersOff =
            debugSection.getSetting(SettingsFile.KEY_DEBUG_JITSYSTEMREGISTEROFF)
        val jitBranchOff = debugSection.getSetting(SettingsFile.KEY_DEBUG_JITBRANCHOFF)
        val jitRegisterCacheOff =
            debugSection.getSetting(SettingsFile.KEY_DEBUG_JITREGISTERCACHEOFF)

        sl.add(HeaderSetting(null, null, R.string.debug_warning, 0))

        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_DEBUG_JITOFF, Settings.SECTION_DEBUG,
                R.string.debug_jitoff, 0, false,
                jitOff
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_DEBUG_JITLOADSTOREOFF, Settings.SECTION_DEBUG,
                R.string.debug_jitloadstoreoff, 0, false,
                jitLoadStoreOff
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_DEBUG_JITLOADSTOREFLOATINGPOINTOFF,
                Settings.SECTION_DEBUG,
                R.string.debug_jitloadstorefloatingoff, 0, false,
                jitLoadStoreFloatingPointOff
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_DEBUG_JITLOADSTOREPAIREDOFF, Settings.SECTION_DEBUG,
                R.string.debug_jitloadstorepairedoff, 0, false,
                jitLoadStorePairedOff
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_DEBUG_JITFLOATINGPOINTOFF, Settings.SECTION_DEBUG,
                R.string.debug_jitfloatingpointoff, 0, false,
                jitFloatingPointOff
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_DEBUG_JITINTEGEROFF, Settings.SECTION_DEBUG,
                R.string.debug_jitintegeroff, 0, false,
                jitIntegerOff
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_DEBUG_JITPAIREDOFF, Settings.SECTION_DEBUG,
                R.string.debug_jitpairedoff, 0, false,
                jitPairedOff
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_DEBUG_JITSYSTEMREGISTEROFF, Settings.SECTION_DEBUG,
                R.string.debug_jitsystemregistersoffr, 0, false,
                jitSystemRegistersOff
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_DEBUG_JITBRANCHOFF, Settings.SECTION_DEBUG,
                R.string.debug_jitbranchoff, 0, false,
                jitBranchOff
            )
        )
        sl.add(
            CheckBoxSetting(
                SettingsFile.KEY_DEBUG_JITREGISTERCACHEOFF, Settings.SECTION_DEBUG,
                R.string.debug_jitregistercacheoff, 0, false,
                jitRegisterCacheOff
            )
        )
    }

    private fun addGcPadSubSettings(sl: ArrayList<SettingsItem>, gcPadNumber: Int, gcPadType: Int) {
        val bindingsSection = settings!!.getSection(Settings.SECTION_BINDINGS)
        val coreSection = settings!!.getSection(Settings.SECTION_INI_CORE)

        if (gcPadType == 1)  // Emulated
        {
            val bindA = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_A + gcPadNumber)
            val bindB = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_B + gcPadNumber)
            val bindX = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_X + gcPadNumber)
            val bindY = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_Y + gcPadNumber)
            val bindZ = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_Z + gcPadNumber)
            val bindStart = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_START + gcPadNumber)
            val bindControlUp =
                bindingsSection.getSetting(SettingsFile.KEY_GCBIND_CONTROL_UP + gcPadNumber)
            val bindControlDown =
                bindingsSection.getSetting(SettingsFile.KEY_GCBIND_CONTROL_DOWN + gcPadNumber)
            val bindControlLeft =
                bindingsSection.getSetting(SettingsFile.KEY_GCBIND_CONTROL_LEFT + gcPadNumber)
            val bindControlRight =
                bindingsSection.getSetting(SettingsFile.KEY_GCBIND_CONTROL_RIGHT + gcPadNumber)
            val bindCUp = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_C_UP + gcPadNumber)
            val bindCDown = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_C_DOWN + gcPadNumber)
            val bindCLeft = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_C_LEFT + gcPadNumber)
            val bindCRight =
                bindingsSection.getSetting(SettingsFile.KEY_GCBIND_C_RIGHT + gcPadNumber)
            val bindTriggerL =
                bindingsSection.getSetting(SettingsFile.KEY_GCBIND_TRIGGER_L + gcPadNumber)
            val bindTriggerR =
                bindingsSection.getSetting(SettingsFile.KEY_GCBIND_TRIGGER_R + gcPadNumber)
            val bindTriggerLAnalog =
                bindingsSection.getSetting(SettingsFile.KEY_GCBIND_TRIGGER_L_ANALOG + gcPadNumber)
            val bindTriggerRAnalog =
                bindingsSection.getSetting(SettingsFile.KEY_GCBIND_TRIGGER_R_ANALOG + gcPadNumber)
            val bindDPadUp =
                bindingsSection.getSetting(SettingsFile.KEY_GCBIND_DPAD_UP + gcPadNumber)
            val bindDPadDown =
                bindingsSection.getSetting(SettingsFile.KEY_GCBIND_DPAD_DOWN + gcPadNumber)
            val bindDPadLeft =
                bindingsSection.getSetting(SettingsFile.KEY_GCBIND_DPAD_LEFT + gcPadNumber)
            val bindDPadRight =
                bindingsSection.getSetting(SettingsFile.KEY_GCBIND_DPAD_RIGHT + gcPadNumber)
            val gcEmuRumble =
                bindingsSection.getSetting(SettingsFile.KEY_EMU_RUMBLE + gcPadNumber)

            sl.add(HeaderSetting(null, null, R.string.generic_buttons, 0))
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_A + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.button_a, bindA
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_B + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.button_b, bindB
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_X + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.button_x, bindX
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_Y + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.button_y, bindY
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_Z + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.button_z, bindZ
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_START + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.button_start, bindStart
                )
            )

            sl.add(HeaderSetting(null, null, R.string.controller_control, 0))
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_CONTROL_UP + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.generic_up, bindControlUp
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_CONTROL_DOWN + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.generic_down, bindControlDown
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_CONTROL_LEFT + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.generic_left, bindControlLeft
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_CONTROL_RIGHT + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.generic_right, bindControlRight
                )
            )

            sl.add(HeaderSetting(null, null, R.string.controller_c, 0))
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_C_UP + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.generic_up, bindCUp
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_C_DOWN + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.generic_down, bindCDown
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_C_LEFT + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.generic_left, bindCLeft
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_C_RIGHT + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.generic_right, bindCRight
                )
            )

            sl.add(HeaderSetting(null, null, R.string.controller_trig, 0))
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_TRIGGER_L + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.trigger_left, bindTriggerL
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_TRIGGER_R + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.trigger_right, bindTriggerR
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_TRIGGER_L_ANALOG + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.trigger_left_analog, bindTriggerLAnalog
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_TRIGGER_R_ANALOG + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.trigger_right_analog, bindTriggerRAnalog
                )
            )

            sl.add(HeaderSetting(null, null, R.string.controller_dpad, 0))
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_DPAD_UP + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.generic_up, bindDPadUp
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_DPAD_DOWN + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.generic_down, bindDPadDown
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_DPAD_LEFT + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.generic_left, bindDPadLeft
                )
            )
            sl.add(
                InputBindingSetting(
                    SettingsFile.KEY_GCBIND_DPAD_RIGHT + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.generic_right, bindDPadRight
                )
            )


            sl.add(HeaderSetting(null, null, R.string.emulation_control_rumble, 0))
            sl.add(
                RumbleBindingSetting(
                    SettingsFile.KEY_EMU_RUMBLE + gcPadNumber,
                    Settings.SECTION_BINDINGS, R.string.emulation_control_rumble, gcEmuRumble
                )
            )
        } else  // Adapter
        {
            val rumble = coreSection.getSetting(SettingsFile.KEY_GCADAPTER_RUMBLE + gcPadNumber)
            val bongos = coreSection.getSetting(SettingsFile.KEY_GCADAPTER_BONGOS + gcPadNumber)

            sl.add(
                CheckBoxSetting(
                    SettingsFile.KEY_GCADAPTER_RUMBLE + gcPadNumber,
                    Settings.SECTION_INI_CORE, R.string.gc_adapter_rumble,
                    R.string.gc_adapter_rumble_description, false, rumble
                )
            )
            sl.add(
                CheckBoxSetting(
                    SettingsFile.KEY_GCADAPTER_BONGOS + gcPadNumber,
                    Settings.SECTION_INI_CORE, R.string.gc_adapter_bongos,
                    R.string.gc_adapter_bongos_description, false, bongos
                )
            )
        }
    }

    private fun addWiimoteSubSettings(sl: ArrayList<SettingsItem>, wiimoteNumber: Int) {
        val bindingsSection = settings!!.getSection(Settings.SECTION_BINDINGS)

        val bindA = bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_A + wiimoteNumber)
        val bindB = bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_B + wiimoteNumber)
        val bind1 = bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_1 + wiimoteNumber)
        val bind2 = bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_2 + wiimoteNumber)
        val bindMinus = bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_MINUS + wiimoteNumber)
        val bindPlus = bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_PLUS + wiimoteNumber)
        val bindHome = bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_HOME + wiimoteNumber)
        val bindIRUp = bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_IR_UP + wiimoteNumber)
        val bindIRDown =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_IR_DOWN + wiimoteNumber)
        val bindIRLeft =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_IR_LEFT + wiimoteNumber)
        val bindIRRight =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_IR_RIGHT + wiimoteNumber)
        val bindIRHide =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_IR_HIDE + wiimoteNumber)
        val bindSwingUp =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SWING_UP + wiimoteNumber)
        val bindSwingDown =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SWING_DOWN + wiimoteNumber)
        val bindSwingLeft =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SWING_LEFT + wiimoteNumber)
        val bindSwingRight =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SWING_RIGHT + wiimoteNumber)
        val bindSwingForward =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SWING_FORWARD + wiimoteNumber)
        val bindSwingBackward =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SWING_BACKWARD + wiimoteNumber)
        val bindTiltForward =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_TILT_FORWARD + wiimoteNumber)
        val bindTiltBackward =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_TILT_BACKWARD + wiimoteNumber)
        val bindTiltLeft =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_TILT_LEFT + wiimoteNumber)
        val bindTiltRight =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_TILT_RIGHT + wiimoteNumber)
        val bindTiltModifier =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_TILT_MODIFIER + wiimoteNumber)
        val bindShakeX =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SHAKE_X + wiimoteNumber)
        val bindShakeY =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SHAKE_Y + wiimoteNumber)
        val bindShakeZ =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SHAKE_Z + wiimoteNumber)
        val bindDPadUp =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DPAD_UP + wiimoteNumber)
        val bindDPadDown =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DPAD_DOWN + wiimoteNumber)
        val bindDPadLeft =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DPAD_LEFT + wiimoteNumber)
        val bindDPadRight =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DPAD_RIGHT + wiimoteNumber)
        val bindSidewaysToggle =
            bindingsSection.getSetting(SettingsFile.KEY_HOTKEYS_SIDEWAYS_TOGGLE + wiimoteNumber)
        val bindUprightToggle =
            bindingsSection.getSetting(SettingsFile.KEY_HOTKEYS_UPRIGHT_TOGGLE + wiimoteNumber)
        val wiiEmuRumble =
            bindingsSection.getSetting(SettingsFile.KEY_EMU_RUMBLE + wiimoteNumber)

        // Bindings use controller numbers 4-7 (0-3 are GameCube), but the extension setting uses 1-4.
        // But game game specific extension settings are saved in their own profile. These profiles
        // do not have any way to specify the controller that is loaded outside of knowing the filename
        // of the profile that was loaded.
        val extension: IntSetting
        if (TextUtils.isEmpty(gameId)) {
            extension = IntSetting(
                SettingsFile.KEY_WIIMOTE_EXTENSION,
                Settings.SECTION_WIIMOTE + wiimoteNumber, getExtensionValue(wiimoteNumber - 3)
            )
            sl.add(
                SingleChoiceSetting(
                    SettingsFile.KEY_WIIMOTE_EXTENSION,
                    Settings.SECTION_WIIMOTE + (wiimoteNumber - 3), R.string.wiimote_extensions, 0,
                    R.array.wiimoteExtensionsEntries, R.array.wiimoteExtensionsValues, 0,
                    extension, getWiimoteExtensionMenuTag(wiimoteNumber)
                )
            )
        } else {
            settings!!.loadWiimoteProfile(gameId, (wiimoteNumber - 4).toString())
            extension = IntSetting(
                SettingsFile.KEY_WIIMOTE_EXTENSION + (wiimoteNumber - 4),
                Settings.SECTION_CONTROLS, getExtensionValue(wiimoteNumber - 4)
            )
            sl.add(
                SingleChoiceSetting(
                    SettingsFile.KEY_WIIMOTE_EXTENSION + (wiimoteNumber - 4),
                    Settings.SECTION_CONTROLS, R.string.wiimote_extensions, 0,
                    R.array.wiimoteExtensionsEntries, R.array.wiimoteExtensionsValues, 0,
                    extension, getWiimoteExtensionMenuTag(wiimoteNumber)
                )
            )
        }

        sl.add(HeaderSetting(null, null, R.string.generic_buttons, 0))
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_A + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_a, bindA
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_B + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_b, bindB
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_1 + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_one, bind1
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_2 + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_two, bind2
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_MINUS + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_minus, bindMinus
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_PLUS + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_plus, bindPlus
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_HOME + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_home, bindHome
            )
        )

        sl.add(HeaderSetting(null, null, R.string.wiimote_ir, 0))
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_IR_UP + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_up, bindIRUp
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_IR_DOWN + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_down, bindIRDown
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_IR_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_left, bindIRLeft
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_IR_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindIRRight
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_IR_HIDE + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.ir_hide, bindIRHide
            )
        )

        sl.add(HeaderSetting(null, null, R.string.wiimote_swing, 0))
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_SWING_UP + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_up, bindSwingUp
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_SWING_DOWN + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_down, bindSwingDown
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_SWING_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_left, bindSwingLeft
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_SWING_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindSwingRight
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_SWING_FORWARD + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_forward, bindSwingForward
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_SWING_BACKWARD + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_backward, bindSwingBackward
            )
        )

        sl.add(HeaderSetting(null, null, R.string.wiimote_tilt, 0))
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TILT_FORWARD + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_forward, bindTiltForward
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TILT_BACKWARD + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_backward, bindTiltBackward
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TILT_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_left, bindTiltLeft
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TILT_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindTiltRight
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TILT_MODIFIER + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.tilt_modifier, bindTiltModifier
            )
        )

        sl.add(HeaderSetting(null, null, R.string.wiimote_shake, 0))
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_SHAKE_X + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.shake_x, bindShakeX
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_SHAKE_Y + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.shake_y, bindShakeY
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_SHAKE_Z + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.shake_z, bindShakeZ
            )
        )

        sl.add(HeaderSetting(null, null, R.string.controller_dpad, 0))
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_DPAD_UP + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_up, bindDPadUp
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_DPAD_DOWN + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_down, bindDPadDown
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_DPAD_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_left, bindDPadLeft
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_WIIBIND_DPAD_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindDPadRight
            )
        )

        sl.add(HeaderSetting(null, null, R.string.wiimote_hotkeys, 0))
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_HOTKEYS_SIDEWAYS_TOGGLE + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.hotkey_sideways_toggle, bindSidewaysToggle
            )
        )
        sl.add(
            InputBindingSetting(
                SettingsFile.KEY_HOTKEYS_UPRIGHT_TOGGLE + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.hotkey_upright_toggle, bindUprightToggle
            )
        )

        sl.add(HeaderSetting(null, null, R.string.emulation_control_rumble, 0))
        sl.add(
            RumbleBindingSetting(
                SettingsFile.KEY_EMU_RUMBLE + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.emulation_control_rumble, wiiEmuRumble
            )
        )
    }

    private fun addExtensionTypeSettings(
        sl: ArrayList<SettingsItem>, wiimoteNumber: Int,
        extentionType: Int
    ) {
        val bindingsSection = settings!!.getSection(Settings.SECTION_BINDINGS)

        when (extentionType) {
            1 -> {
                val bindC =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_C + wiimoteNumber)
                val bindZ =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_Z + wiimoteNumber)
                val bindUp =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_UP + wiimoteNumber)
                val bindDown =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_DOWN + wiimoteNumber)
                val bindLeft =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_LEFT + wiimoteNumber)
                val bindRight =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_RIGHT + wiimoteNumber)
                val bindSwingUp = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_UP + wiimoteNumber)
                val bindSwingDown = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_DOWN + wiimoteNumber)
                val bindSwingLeft = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_LEFT + wiimoteNumber)
                val bindSwingRight = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_RIGHT + wiimoteNumber)
                val bindSwingForward = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_FORWARD + wiimoteNumber)
                val bindSwingBackward = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_BACKWARD + wiimoteNumber)
                val bindTiltForward = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_FORWARD + wiimoteNumber)
                val bindTiltBackward = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_BACKWARD + wiimoteNumber)
                val bindTiltLeft = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_LEFT + wiimoteNumber)
                val bindTiltRight = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_RIGHT + wiimoteNumber)
                val bindTiltModifier = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_MODIFIER + wiimoteNumber)
                val bindShakeX = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SHAKE_X + wiimoteNumber)
                val bindShakeY = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SHAKE_Y + wiimoteNumber)
                val bindShakeZ = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SHAKE_Z + wiimoteNumber)

                sl.add(HeaderSetting(null, null, R.string.generic_buttons, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_C + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.nunchuk_button_c, bindC
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_Z + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.button_z, bindZ
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.generic_stick, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_UP + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_up, bindUp
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_DOWN + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_down, bindDown
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_LEFT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_left, bindLeft
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_RIGHT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_right, bindRight
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.wiimote_swing, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_UP + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_up, bindSwingUp
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_DOWN + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_down, bindSwingDown
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_LEFT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_left, bindSwingLeft
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_RIGHT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_right, bindSwingRight
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_FORWARD + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_forward, bindSwingForward
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_BACKWARD + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_backward, bindSwingBackward
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.wiimote_tilt, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_FORWARD + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_forward, bindTiltForward
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_BACKWARD + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_backward, bindTiltBackward
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_LEFT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_left, bindTiltLeft
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_RIGHT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_right, bindTiltRight
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_MODIFIER + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.tilt_modifier, bindTiltModifier
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.wiimote_shake, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_SHAKE_X + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.shake_x, bindShakeX
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_SHAKE_Y + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.shake_y, bindShakeY
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_NUNCHUK_SHAKE_Z + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.shake_z, bindShakeZ
                    )
                )
            }

            2 -> {
                val bindA =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_A + wiimoteNumber)
                val bindB =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_B + wiimoteNumber)
                val bindX =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_X + wiimoteNumber)
                val bindY =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_Y + wiimoteNumber)
                val bindZL =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_ZL + wiimoteNumber)
                val bindZR =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_ZR + wiimoteNumber)
                val bindMinus =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_MINUS + wiimoteNumber)
                val bindPlus =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_PLUS + wiimoteNumber)
                val bindHome =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_HOME + wiimoteNumber)
                val bindLeftUp = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_LEFT_UP + wiimoteNumber)
                val bindLeftDown = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_LEFT_DOWN + wiimoteNumber)
                val bindLeftLeft = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_LEFT_LEFT + wiimoteNumber)
                val bindLeftRight = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_LEFT_RIGHT + wiimoteNumber)
                val bindRightUp = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_RIGHT_UP + wiimoteNumber)
                val bindRightDown = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_RIGHT_DOWN + wiimoteNumber)
                val bindRightLeft = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_RIGHT_LEFT + wiimoteNumber)
                val bindRightRight = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_RIGHT_RIGHT + wiimoteNumber)
                val bindL = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_TRIGGER_L + wiimoteNumber)
                val bindR = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_TRIGGER_R + wiimoteNumber)
                val bindDpadUp = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_DPAD_UP + wiimoteNumber)
                val bindDpadDown = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_DPAD_DOWN + wiimoteNumber)
                val bindDpadLeft = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_DPAD_LEFT + wiimoteNumber)
                val bindDpadRight = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_DPAD_RIGHT + wiimoteNumber)

                sl.add(HeaderSetting(null, null, R.string.generic_buttons, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_A + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.button_a, bindA
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_B + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.button_b, bindB
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_X + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.button_x, bindX
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_Y + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.button_y, bindY
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_ZL + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.classic_button_zl, bindZL
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_ZR + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.classic_button_zr, bindZR
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_MINUS + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.button_minus, bindMinus
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_PLUS + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.button_plus, bindPlus
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_HOME + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.button_home, bindHome
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.classic_leftstick, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_LEFT_UP + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_up, bindLeftUp
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_LEFT_DOWN + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_down, bindLeftDown
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_LEFT_LEFT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_left, bindLeftLeft
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_LEFT_RIGHT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_right, bindLeftRight
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.classic_rightstick, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_RIGHT_UP + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_up, bindRightUp
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_RIGHT_DOWN + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_down, bindRightDown
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_RIGHT_LEFT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_left, bindRightLeft
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_RIGHT_RIGHT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_right, bindRightRight
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.controller_trig, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_TRIGGER_L + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.trigger_left, bindR
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_TRIGGER_R + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.trigger_right, bindL
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.controller_dpad, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_DPAD_UP + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_up, bindDpadUp
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_DPAD_DOWN + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_down, bindDpadDown
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_DPAD_LEFT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_left, bindDpadLeft
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_CLASSIC_DPAD_RIGHT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_right, bindDpadRight
                    )
                )
            }

            3 -> {
                val bindFretGreen = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_FRET_GREEN + wiimoteNumber)
                val bindFretRed = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_FRET_RED + wiimoteNumber)
                val bindFretYellow = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_FRET_YELLOW + wiimoteNumber)
                val bindFretBlue = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_FRET_BLUE + wiimoteNumber)
                val bindFretOrange = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_FRET_ORANGE + wiimoteNumber)
                val bindStrumUp = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_STRUM_UP + wiimoteNumber)
                val bindStrumDown = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_STRUM_DOWN + wiimoteNumber)
                val bindGuitarMinus =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_GUITAR_MINUS + wiimoteNumber)
                val bindGuitarPlus =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_GUITAR_PLUS + wiimoteNumber)
                val bindGuitarUp = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_STICK_UP + wiimoteNumber)
                val bindGuitarDown = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_STICK_DOWN + wiimoteNumber)
                val bindGuitarLeft = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_STICK_LEFT + wiimoteNumber)
                val bindGuitarRight = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_STICK_RIGHT + wiimoteNumber)
                val bindWhammyBar = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_WHAMMY_BAR + wiimoteNumber)

                sl.add(HeaderSetting(null, null, R.string.guitar_frets, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_GUITAR_FRET_GREEN + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_green, bindFretGreen
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_GUITAR_FRET_RED + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_red, bindFretRed
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_GUITAR_FRET_YELLOW + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_yellow, bindFretYellow
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_GUITAR_FRET_BLUE + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_blue, bindFretBlue
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_GUITAR_FRET_ORANGE + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_orange, bindFretOrange
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.guitar_strum, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_GUITAR_STRUM_UP + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_up, bindStrumUp
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_GUITAR_STRUM_DOWN + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_down, bindStrumDown
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.generic_buttons, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_GUITAR_MINUS + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.button_minus, bindGuitarMinus
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_GUITAR_PLUS + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.button_plus, bindGuitarPlus
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.generic_stick, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_GUITAR_STICK_UP + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_up, bindGuitarUp
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_GUITAR_STICK_DOWN + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_down, bindGuitarDown
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_GUITAR_STICK_LEFT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_left, bindGuitarLeft
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_GUITAR_STICK_RIGHT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_right, bindGuitarRight
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.guitar_whammy, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_GUITAR_WHAMMY_BAR + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_right, bindWhammyBar
                    )
                )
            }

            4 -> {
                val bindPadRed =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_RED + wiimoteNumber)
                val bindPadYellow = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_YELLOW + wiimoteNumber)
                val bindPadBlue =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_BLUE + wiimoteNumber)
                val bindPadGreen = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_GREEN + wiimoteNumber)
                val bindPadOrange = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_ORANGE + wiimoteNumber)
                val bindPadBass =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_BASS + wiimoteNumber)
                val bindDrumsUp =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DRUMS_STICK_UP + wiimoteNumber)
                val bindDrumsDown = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_DRUMS_STICK_DOWN + wiimoteNumber)
                val bindDrumsLeft = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_DRUMS_STICK_LEFT + wiimoteNumber)
                val bindDrumsRight = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_DRUMS_STICK_RIGHT + wiimoteNumber)
                val bindDrumsMinus =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DRUMS_MINUS + wiimoteNumber)
                val bindDrumsPlus =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DRUMS_PLUS + wiimoteNumber)

                sl.add(HeaderSetting(null, null, R.string.drums_pads, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_DRUMS_PAD_RED + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_red, bindPadRed
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_DRUMS_PAD_YELLOW + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_yellow, bindPadYellow
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_DRUMS_PAD_BLUE + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_blue, bindPadBlue
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_DRUMS_PAD_GREEN + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_green, bindPadGreen
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_DRUMS_PAD_ORANGE + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_orange, bindPadOrange
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_DRUMS_PAD_BASS + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.drums_pad_bass, bindPadBass
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.generic_stick, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_DRUMS_STICK_UP + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_up, bindDrumsUp
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_DRUMS_STICK_DOWN + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_down, bindDrumsDown
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_DRUMS_STICK_LEFT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_left, bindDrumsLeft
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_DRUMS_STICK_RIGHT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_right, bindDrumsRight
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.generic_buttons, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_DRUMS_MINUS + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.button_minus, bindDrumsMinus
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_DRUMS_PLUS + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.button_plus, bindDrumsPlus
                    )
                )
            }

            5 -> {
                val bindGreenLeft = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_GREEN_LEFT + wiimoteNumber)
                val bindRedLeft = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_RED_LEFT + wiimoteNumber)
                val bindBlueLeft = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_BLUE_LEFT + wiimoteNumber)
                val bindGreenRight = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_GREEN_RIGHT + wiimoteNumber)
                val bindRedRight = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_RED_RIGHT + wiimoteNumber)
                val bindBlueRight = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_BLUE_RIGHT + wiimoteNumber)
                val bindTurntableMinus = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_MINUS + wiimoteNumber)
                val bindTurntablePlus =
                    bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_PLUS + wiimoteNumber)
                val bindEuphoria = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_EUPHORIA + wiimoteNumber)
                val bindTurntableLeftLeft = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_LEFT_LEFT + wiimoteNumber)
                val bindTurntableLeftRight = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_LEFT_RIGHT + wiimoteNumber)
                val bindTurntableRightLeft = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_RIGHT_LEFT + wiimoteNumber)
                val bindTurntableRightRight = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_RIGHT_RIGHT + wiimoteNumber)
                val bindTurntableUp = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_STICK_UP + wiimoteNumber)
                val bindTurntableDown = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_STICK_DOWN + wiimoteNumber)
                val bindTurntableLeft = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_STICK_LEFT + wiimoteNumber)
                val bindTurntableRight = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_STICK_RIGHT + wiimoteNumber)
                val bindEffectDial = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_EFFECT_DIAL + wiimoteNumber)
                val bindCrossfadeLeft = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_CROSSFADE_LEFT + wiimoteNumber)
                val bindCrossfadeRight = bindingsSection
                    .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_CROSSFADE_RIGHT + wiimoteNumber)

                sl.add(HeaderSetting(null, null, R.string.generic_buttons, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_GREEN_LEFT + wiimoteNumber,
                        Settings.SECTION_BINDINGS,
                        R.string.turntable_button_green_left,
                        bindGreenLeft
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_RED_LEFT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.turntable_button_red_left, bindRedLeft
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_BLUE_LEFT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.turntable_button_blue_left, bindBlueLeft
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_GREEN_RIGHT + wiimoteNumber,
                        Settings.SECTION_BINDINGS,
                        R.string.turntable_button_green_right,
                        bindGreenRight
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_RED_RIGHT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.turntable_button_red_right, bindRedRight
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_BLUE_RIGHT + wiimoteNumber,
                        Settings.SECTION_BINDINGS,
                        R.string.turntable_button_blue_right,
                        bindBlueRight
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_MINUS + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.button_minus, bindTurntableMinus
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_PLUS + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.button_plus, bindTurntablePlus
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_EUPHORIA + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.turntable_button_euphoria, bindEuphoria
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.turntable_table_left, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_LEFT_LEFT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_left, bindTurntableLeftLeft
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_LEFT_RIGHT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_right, bindTurntableLeftRight
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.turntable_table_right, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_RIGHT_LEFT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_left, bindTurntableRightLeft
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_RIGHT_RIGHT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_right, bindTurntableRightRight
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.generic_stick, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_STICK_UP + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_up, bindTurntableUp
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_STICK_DOWN + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_down, bindTurntableDown
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_STICK_LEFT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_left, bindTurntableLeft
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_STICK_RIGHT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_right, bindTurntableRight
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.turntable_effect, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_EFFECT_DIAL + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.turntable_effect_dial, bindEffectDial
                    )
                )

                sl.add(HeaderSetting(null, null, R.string.turntable_crossfade, 0))
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_CROSSFADE_LEFT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_left, bindCrossfadeLeft
                    )
                )
                sl.add(
                    InputBindingSetting(
                        SettingsFile.KEY_WIIBIND_TURNTABLE_CROSSFADE_RIGHT + wiimoteNumber,
                        Settings.SECTION_BINDINGS, R.string.generic_right, bindCrossfadeRight
                    )
                )
            }
        }
    }

    private fun capitalize(text: String): String {
        var text = text
        if (text.contains("_")) {
            text = text.replace("_", " ")
        }

        if (text.length > 1 && text.contains(" ")) {
            val ss = text.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            text = capitalize(ss[0])
            for (i in 1 until ss.size) {
                text += " " + capitalize(ss[i])
            }
            return text
        }

        return text.substring(0, 1).uppercase(Locale.getDefault()) + text.substring(1)
    }

    private fun getSettingEntries(values: Array<String>): Array<String?> {
        val entries = arrayOfNulls<String>(values.size)
        for (i in values.indices) {
            if (values[i].isEmpty()) {
                entries[i] = activity.getString(R.string.off)
            } else {
                entries[i] = capitalize(values[i])
            }
        }
        return entries
    }

    private fun getThemeValues(directoryName: String): Array<String> {
        val path = DirectoryInitialization.getThemeDirectory() + "/$directoryName/"
        val values = getFileList(path, ".zip")
        return values.toTypedArray<String>()
    }

    private fun getGameCubeThemeValues() = getThemeValues("GameCube")
    private fun getDpadThemeValues() = getThemeValues("Dpad")
    private fun getJoystickThemeValues() = getThemeValues("Joystick")
    private fun getWiimoteThemeValues() = getThemeValues("Wiimote")
    private fun getClassicThemeValues() = getThemeValues("Classic")

    private fun getFileList(path: String, ext: String): List<String> {
        val values: MutableList<String> = ArrayList()
        val file = File(path)
        val files = file.listFiles()
        if (files != null) {
            for (i in files.indices) {
                val name = files[i].name
                val extensionIndex = name.indexOf(ext)
                if (extensionIndex > 0) {
                    values.add(name.substring(0, extensionIndex))
                }
            }
        }
        return values
    }

    private val videoBackendValue: Int
        get() {
            val coreSection =
                settings!!.getSection(Settings.SECTION_INI_CORE)

            var videoBackendValue: Int

            try {
                val videoBackend =
                    (coreSection.getSetting(SettingsFile.KEY_VIDEO_BACKEND) as StringSetting).value
                videoBackendValue = when (videoBackend) {
                    "OGL" -> {
                        0
                    }
                    "Vulkan" -> {
                        1
                    }
                    "Software Renderer" -> {
                        2
                    }
                    "Null" -> {
                        3
                    }
                    else -> {
                        0
                    }
                }
            } catch (ex: NullPointerException) {
                videoBackendValue = 0
            }

            return videoBackendValue
        }

    private fun getExtensionValue(wiimoteNumber: Int): Int {
        var extensionValue: Int

        try {
            val extension = if (TextUtils.isEmpty(gameId))  // Main settings
            {
                (settings!!.getSection(Settings.SECTION_WIIMOTE + wiimoteNumber)
                    .getSetting(SettingsFile.KEY_WIIMOTE_EXTENSION) as StringSetting).value
            } else  // Game settings
            {
                (settings!!.getSection(Settings.SECTION_PROFILE)
                    .getSetting(SettingsFile.KEY_WIIMOTE_EXTENSION) as StringSetting).value
            }

            extensionValue = when (extension) {
                "None" -> {
                    0
                }
                "Nunchuk" -> {
                    1
                }
                "Classic" -> {
                    2
                }
                "Guitar" -> {
                    3
                }
                "Drums" -> {
                    4
                }
                "Turntable" -> {
                    5
                }
                else -> {
                    0
                }
            }
        } catch (ex: NullPointerException) {
            extensionValue = 0
        }

        return extensionValue
    }
}
