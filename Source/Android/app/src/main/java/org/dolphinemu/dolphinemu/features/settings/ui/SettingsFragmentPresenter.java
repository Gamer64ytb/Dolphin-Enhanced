package org.dolphinemu.dolphinemu.features.settings.ui;

import android.os.Bundle;
import android.text.TextUtils;

import org.dolphinemu.dolphinemu.NativeLibrary;
import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.features.settings.model.BooleanSetting;
import org.dolphinemu.dolphinemu.features.settings.model.IntSetting;
import org.dolphinemu.dolphinemu.features.settings.model.Setting;
import org.dolphinemu.dolphinemu.features.settings.model.SettingSection;
import org.dolphinemu.dolphinemu.features.settings.model.Settings;
import org.dolphinemu.dolphinemu.features.settings.model.StringSetting;
import org.dolphinemu.dolphinemu.features.settings.model.view.CheckBoxSetting;
import org.dolphinemu.dolphinemu.features.settings.model.view.HeaderSetting;
import org.dolphinemu.dolphinemu.features.settings.model.view.InputBindingSetting;
import org.dolphinemu.dolphinemu.features.settings.model.view.RumbleBindingSetting;
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem;
import org.dolphinemu.dolphinemu.features.settings.model.view.SingleChoiceSetting;
import org.dolphinemu.dolphinemu.features.settings.model.view.SliderSetting;
import org.dolphinemu.dolphinemu.features.settings.model.view.StringSingleChoiceSetting;
import org.dolphinemu.dolphinemu.features.settings.model.view.SubmenuSetting;
import org.dolphinemu.dolphinemu.features.settings.utils.SettingsFile;
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class SettingsFragmentPresenter
{
  private MenuTag mMenuTag;
  private String mGameID;
  private Settings mSettings;
  private SettingsActivity mActivity;

  private int mControllerNumber;
  private int mControllerType;

  public SettingsFragmentPresenter(SettingsActivity activity)
  {
    mActivity = activity;
  }

  public void onCreate(MenuTag menuTag, String gameId, Bundle extras)
  {
    mGameID = gameId;
    mMenuTag = menuTag;

    if (menuTag.isGCPadMenu() || menuTag.isWiimoteExtensionMenu())
    {
      mControllerNumber = menuTag.getSubType();
      mControllerType = extras.getInt(SettingsActivity.ARG_CONTROLLER_TYPE);
    }
    else if (menuTag.isWiimoteMenu())
    {
      mControllerNumber = menuTag.getSubType();
    }
  }

  /**
   * If the screen is rotated, the Activity will forget the settings map. This fragment
   * won't, though; so rather than have the Activity reload from disk, have the fragment pass
   * the settings map back to the Activity.
   */
  public void onAttach()
  {
    if (mSettings != null)
    {
      mActivity.setSettings(mSettings);
    }
  }

  public ArrayList<SettingsItem> loadSettingsList(Settings settings)
  {
    ArrayList<SettingsItem> sl = new ArrayList<>();
    mSettings = settings;
    switch (mMenuTag)
    {
      case CONFIG:
        addConfigSettings(sl);
        break;

      case CONFIG_GENERAL:
        addGeneralSettings(sl);
        break;

      case CONFIG_INTERFACE:
        addInterfaceSettings(sl);
        break;

      case CONFIG_GAME_CUBE:
        addGameCubeSettings(sl);
        break;

      case CONFIG_WII:
        addWiiSettings(sl);
        break;

      case GRAPHICS:
        addGraphicsSettings(sl);
        break;

      case GCPAD_TYPE:
        addGcPadSettings(sl);
        break;

      case WIIMOTE:
        addWiimoteSettings(sl);
        break;

      case ENHANCEMENTS:
        addEnhanceSettings(sl);
        break;

      case HACKS:
        addHackSettings(sl);
        break;

      case DEBUG:
        addDebugSettings(sl);
        break;

      case GCPAD_1:
      case GCPAD_2:
      case GCPAD_3:
      case GCPAD_4:
        addGcPadSubSettings(sl, mControllerNumber, mControllerType);
        break;

      case WIIMOTE_1:
      case WIIMOTE_2:
      case WIIMOTE_3:
      case WIIMOTE_4:
        addWiimoteSubSettings(sl, mControllerNumber);
        break;

      case WIIMOTE_EXTENSION_1:
      case WIIMOTE_EXTENSION_2:
      case WIIMOTE_EXTENSION_3:
      case WIIMOTE_EXTENSION_4:
        addExtensionTypeSettings(sl, mControllerNumber, mControllerType);
        break;

      default:
        return null;
    }
    return sl;
  }

  private void addConfigSettings(ArrayList<SettingsItem> sl)
  {
    sl.add(new SubmenuSetting(null, null, R.string.general_submenu, 0, MenuTag.CONFIG_GENERAL));
    sl.add(new SubmenuSetting(null, null, R.string.grid_menu_graphics_settings, 0, MenuTag.GRAPHICS));
    sl.add(new SubmenuSetting(null, null, R.string.enhancements_submenu, 0, MenuTag.ENHANCEMENTS));
    sl.add(new SubmenuSetting(null, null, R.string.hacks_submenu, 0, MenuTag.HACKS));
    sl.add(new SubmenuSetting(null, null, R.string.interface_submenu, 0, MenuTag.CONFIG_INTERFACE));
    sl.add(new SubmenuSetting(null, null, R.string.gamecube_submenu, 0, MenuTag.CONFIG_GAME_CUBE));
    sl.add(new SubmenuSetting(null, null, R.string.wii_submenu, 0, MenuTag.CONFIG_WII));
    sl.add(new SubmenuSetting(null, null, R.string.debug_submenu, 0, MenuTag.DEBUG));
  }

  private void addGeneralSettings(ArrayList<SettingsItem> sl)
  {
    SettingSection coreSection = mSettings.getSection(Settings.SECTION_INI_CORE);
    Setting cpuCore = coreSection.getSetting(SettingsFile.KEY_CPU_CORE);
    Setting dualCore = coreSection.getSetting(SettingsFile.KEY_DUAL_CORE);
    Setting overclockEnable = coreSection.getSetting(SettingsFile.KEY_OVERCLOCK_ENABLE);
    Setting overclock = coreSection.getSetting(SettingsFile.KEY_OVERCLOCK_PERCENT);
    Setting speedLimit = coreSection.getSetting(SettingsFile.KEY_SPEED_LIMIT);
    Setting syncOnSkipIdle = coreSection.getSetting(SettingsFile.KEY_SYNC_ON_SKIP_IDLE);
    Setting mmu = coreSection.getSetting(SettingsFile.KEY_MMU);
    Setting fastDiscSpeed = coreSection.getSetting(SettingsFile.KEY_FAST_DISC_SPEED);
    Setting followBranch = coreSection.getSetting(SettingsFile.KEY_JIT_FOLLOW_BRANCH);
    Setting overrideRegionSettings = coreSection.getSetting(SettingsFile.KEY_OVERRIDE_REGION_SETTINGS);
    Setting autoDiscChange = coreSection.getSetting(SettingsFile.KEY_AUTO_DISC_CHANGE);
    Setting audioStretch = coreSection.getSetting(SettingsFile.KEY_AUDIO_STRETCH);
    Setting stretchLatency = coreSection.getSetting(SettingsFile.KEY_AUDIO_STRETCH_MAX_LATENCY);
    Setting audioBackend = mSettings.getSection(Settings.SECTION_INI_DSP)
      .getSetting(SettingsFile.KEY_AUDIO_BACKEND);
    Setting enableCheats = coreSection.getSetting(SettingsFile.KEY_ENABLE_CHEATS);

    // TODO: Having different emuCoresEntries/emuCoresValues for each architecture is annoying.
    // The proper solution would be to have one emuCoresEntries and one emuCoresValues
    // and exclude the values that aren't present in PowerPC::AvailableCPUCores().
    int defaultCpuCore = NativeLibrary.DefaultCPUCore();
    int emuCoresEntries;
    int emuCoresValues;
    if (defaultCpuCore == 4)  // AArch64
    {
      emuCoresEntries = R.array.emuCoresEntriesARM64;
      emuCoresValues = R.array.emuCoresValuesARM64;
    }
    else
    {
      emuCoresEntries = R.array.emuCoresEntriesGeneric;
      emuCoresValues = R.array.emuCoresValuesGeneric;
    }
    sl.add(new SingleChoiceSetting(SettingsFile.KEY_CPU_CORE, Settings.SECTION_INI_CORE,
      R.string.cpu_core, 0, emuCoresEntries, emuCoresValues, defaultCpuCore, cpuCore));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_DUAL_CORE, Settings.SECTION_INI_CORE,
      R.string.dual_core, R.string.dual_core_description, true, dualCore));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_OVERCLOCK_ENABLE, Settings.SECTION_INI_CORE,
      R.string.overclock_enable, R.string.overclock_enable_description, false,
      overclockEnable));
    sl.add(new SliderSetting(SettingsFile.KEY_OVERCLOCK_PERCENT, Settings.SECTION_INI_CORE,
      R.string.overclock_title, R.string.overclock_title_description, 300, "%", 100,
      overclock));
    sl.add(new SliderSetting(SettingsFile.KEY_SPEED_LIMIT, Settings.SECTION_INI_CORE,
      R.string.speed_limit, 0, 200, "%", 100, speedLimit));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_SYNC_ON_SKIP_IDLE, Settings.SECTION_INI_CORE,
      R.string.sync_on_skip_idle, R.string.sync_on_skip_idle_description, true,
      syncOnSkipIdle));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_MMU, Settings.SECTION_INI_CORE,
      R.string.mmu_enable, R.string.mmu_enable_description, false, mmu));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_FAST_DISC_SPEED, Settings.SECTION_INI_CORE,
      R.string.fast_disc_speed, R.string.fast_disc_speed_description, false, fastDiscSpeed));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_JIT_FOLLOW_BRANCH, Settings.SECTION_INI_CORE,
      R.string.jit_follow_branch, R.string.jit_follow_branch_description, true,
      followBranch));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_ENABLE_CHEATS, Settings.SECTION_INI_CORE,
      R.string.enable_cheats, R.string.enable_cheats_description, false, enableCheats));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_OVERRIDE_REGION_SETTINGS, Settings.SECTION_INI_CORE,
      R.string.override_region_settings, 0, false, overrideRegionSettings));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_AUTO_DISC_CHANGE, Settings.SECTION_INI_CORE,
      R.string.auto_disc_change, 0, false, autoDiscChange));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_AUDIO_STRETCH, Settings.SECTION_INI_CORE,
      R.string.audio_stretch, R.string.audio_stretch_description, false, audioStretch));
    sl.add(new SliderSetting(SettingsFile.KEY_AUDIO_STRETCH_MAX_LATENCY, Settings.SECTION_INI_CORE,
      R.string.audio_stretch_max_latency, R.string.audio_stretch_max_latency_description, 300, "",
      80, stretchLatency));

    String defaultAudioBackend = NativeLibrary.DefaultAudioBackend();
    String[] audioListEntries = NativeLibrary.GetAudioBackendList();
    String[] audioListValues = new String[audioListEntries.length];
    System.arraycopy(audioListEntries, 0, audioListValues, 0, audioListEntries.length);
    sl.add(new StringSingleChoiceSetting(SettingsFile.KEY_AUDIO_BACKEND, Settings.SECTION_INI_DSP,
      R.string.audio_backend, 0, audioListEntries,
      audioListValues, defaultAudioBackend, audioBackend));
  }

  private void addInterfaceSettings(ArrayList<SettingsItem> sl)
  {
    SettingSection uiSection = mSettings.getSection(Settings.SECTION_INI_INTERFACE);
    Setting usePanicHandlers = uiSection.getSetting(SettingsFile.KEY_USE_PANIC_HANDLERS);
    Setting onScreenDisplayMessages = uiSection.getSetting(SettingsFile.KEY_OSD_MESSAGES);
    Setting useBuiltinTitleDatabase = uiSection.getSetting(SettingsFile.KEY_BUILTIN_TITLE_DATABASE);
    Setting systemBack = uiSection.getSetting(SettingsFile.KEY_SYSTEM_BACK);

    sl.add(new CheckBoxSetting(SettingsFile.KEY_USE_PANIC_HANDLERS, Settings.SECTION_INI_INTERFACE,
      R.string.panic_handlers, R.string.panic_handlers_description, true, usePanicHandlers));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_OSD_MESSAGES, Settings.SECTION_INI_INTERFACE,
      R.string.osd_messages, R.string.osd_messages_description, true,
      onScreenDisplayMessages));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_BUILTIN_TITLE_DATABASE, Settings.SECTION_INI_INTERFACE,
      R.string.use_builtin_title_database, 0, true, useBuiltinTitleDatabase));
    sl.add(new InputBindingSetting(SettingsFile.KEY_SYSTEM_BACK, Settings.SECTION_INI_INTERFACE,
      R.string.system_back, systemBack));
  }

  private void addGameCubeSettings(ArrayList<SettingsItem> sl)
  {
    SettingSection coreSection = mSettings.getSection(Settings.SECTION_INI_CORE);
    Setting systemLanguage = coreSection.getSetting(SettingsFile.KEY_GAME_CUBE_LANGUAGE);
    Setting slotADevice = coreSection.getSetting(SettingsFile.KEY_SLOT_A_DEVICE);
    Setting slotBDevice = coreSection.getSetting(SettingsFile.KEY_SLOT_B_DEVICE);
    Setting serialDevice = coreSection.getSetting(SettingsFile.KEY_SERIAL_PORT_1);

    sl.add(new SingleChoiceSetting(SettingsFile.KEY_GAME_CUBE_LANGUAGE, Settings.SECTION_INI_CORE,
      R.string.gamecube_system_language, 0, R.array.gameCubeSystemLanguageEntries,
      R.array.gameCubeSystemLanguageValues, 0, systemLanguage));
    sl.add(new SingleChoiceSetting(SettingsFile.KEY_SLOT_A_DEVICE, Settings.SECTION_INI_CORE,
      R.string.slot_a_device, 0, R.array.slotDeviceEntries, R.array.slotDeviceValues, 8,
      slotADevice));
    sl.add(new SingleChoiceSetting(SettingsFile.KEY_SLOT_B_DEVICE, Settings.SECTION_INI_CORE,
      R.string.slot_b_device, 0, R.array.slotDeviceEntries, R.array.slotDeviceValues, 255,
      slotBDevice));
    sl.add(new SingleChoiceSetting(SettingsFile.KEY_SERIAL_PORT_1, Settings.SECTION_INI_CORE,
      R.string.serial_port_1, 0, R.array.serialDeviceEntries, R.array.serialDeviceValues, 255,
      serialDevice));
  }

  private void addWiiSettings(ArrayList<SettingsItem> sl)
  {
    SettingSection coreSection = mSettings.getSection(Settings.SECTION_INI_CORE);
    Setting continuousScan = coreSection.getSetting(SettingsFile.KEY_WIIMOTE_SCAN);
    Setting wiimoteSpeaker = coreSection.getSetting(SettingsFile.KEY_WIIMOTE_SPEAKER);
    Setting wiiSDCard = coreSection.getSetting(SettingsFile.KEY_WII_SD_CARD);

    sl.add(new CheckBoxSetting(SettingsFile.KEY_WIIMOTE_SCAN, Settings.SECTION_INI_CORE,
      R.string.wiimote_scanning, R.string.wiimote_scanning_description, true,
      continuousScan));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_WIIMOTE_SPEAKER, Settings.SECTION_INI_CORE,
      R.string.wiimote_speaker, R.string.wiimote_speaker_description, true, wiimoteSpeaker));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_WII_SD_CARD, Settings.SECTION_INI_CORE,
      R.string.wii_sd_card, R.string.wii_sd_card_description, false, wiiSDCard));

    // SYSCONF_SETTINGS
    SettingSection sysconfSection = mSettings.getSection(Settings.SECTION_WII_IPL);
    Setting screensaver = sysconfSection.getSetting(SettingsFile.KEY_SYSCONF_SCREENSAVER);
    Setting language = sysconfSection.getSetting(SettingsFile.KEY_SYSCONF_LANGUAGE);
    Setting widescreen = sysconfSection.getSetting(SettingsFile.KEY_SYSCONF_WIDESCREEN);
    Setting progressiveScan = sysconfSection.getSetting(SettingsFile.KEY_SYSCONF_PROGRESSIVE_SCAN);
    Setting pal60 = sysconfSection.getSetting(SettingsFile.KEY_SYSCONF_PAL60);

    sl.add(new CheckBoxSetting(SettingsFile.KEY_SYSCONF_SCREENSAVER, Settings.SECTION_WII_IPL,
      R.string.sysconf_screensaver, 0, false, screensaver));
    sl.add(new SingleChoiceSetting(SettingsFile.KEY_SYSCONF_LANGUAGE, Settings.SECTION_WII_IPL,
      R.string.sysconf_language, 0, R.array.wiiSystemLanguageEntries,
      R.array.wiiSystemLanguageValues, 0, language));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_SYSCONF_WIDESCREEN, Settings.SECTION_WII_IPL,
      R.string.sysconf_widescreen, 0, true, widescreen));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_SYSCONF_PROGRESSIVE_SCAN, Settings.SECTION_WII_IPL,
      R.string.sysconf_progressive_scan, 0, true, progressiveScan));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_SYSCONF_PAL60, Settings.SECTION_WII_IPL,
      R.string.sysconf_pal60, 0, true, pal60));
  }

  private void addGcPadSettings(ArrayList<SettingsItem> sl)
  {
    for (int i = 0; i < 4; i++)
    {
      if (TextUtils.isEmpty(mGameID))
      {
        // TODO This controller_0 + i business is quite the hack. It should work, but only if the definitions are kept together and in order.
        Setting gcPadSetting = mSettings.getSection(Settings.SECTION_INI_CORE)
                .getSetting(SettingsFile.KEY_GCPAD_TYPE + i);
        sl.add(new SingleChoiceSetting(SettingsFile.KEY_GCPAD_TYPE + i, Settings.SECTION_INI_CORE,
                R.string.controller_0 + i, 0, R.array.gcpadTypeEntries, R.array.gcpadTypeValues, 0,
                gcPadSetting, MenuTag.getGCPadMenuTag(i)));
      }
      else
      {
        Setting gcPadSetting = mSettings.getSection(Settings.SECTION_CONTROLS)
                .getSetting(SettingsFile.KEY_GCPAD_G_TYPE + i);
        sl.add(new SingleChoiceSetting(SettingsFile.KEY_GCPAD_G_TYPE + i, Settings.SECTION_CONTROLS,
                R.string.controller_0 + i, 0, R.array.gcpadTypeEntries, R.array.gcpadTypeValues, 0,
                gcPadSetting, MenuTag.getGCPadMenuTag(i)));
      }
    }
  }

  private void addWiimoteSettings(ArrayList<SettingsItem> sl)
  {
    for (int i = 0; i < 4; i++)
    {
      // TODO This wiimote_0 + i business is quite the hack. It should work, but only if the definitions are kept together and in order.
      if (TextUtils.isEmpty(mGameID))
      {
        Setting wiimoteSetting = mSettings.getSection(Settings.SECTION_WIIMOTE + (i + 1))
                .getSetting(SettingsFile.KEY_WIIMOTE_TYPE);
        sl.add(new SingleChoiceSetting(SettingsFile.KEY_WIIMOTE_TYPE,
                Settings.SECTION_WIIMOTE + (i + 1), R.string.wiimote_4 + i, 0,
                R.array.wiimoteTypeEntries, R.array.wiimoteTypeValues, 0, wiimoteSetting,
                MenuTag.getWiimoteMenuTag(i + 4)));
      }
      else
      {
        Setting wiimoteSetting = mSettings.getSection(Settings.SECTION_CONTROLS)
                .getSetting(SettingsFile.KEY_WIIMOTE_G_TYPE + i);
        sl.add(new SingleChoiceSetting(SettingsFile.KEY_WIIMOTE_G_TYPE + i,
                Settings.SECTION_CONTROLS, R.string.wiimote_4 + i, 0, R.array.wiimoteTypeEntries,
                R.array.wiimoteTypeValues, 0, wiimoteSetting, MenuTag.getWiimoteMenuTag(i + 4)));
      }
    }
  }

  private void addGraphicsSettings(ArrayList<SettingsItem> sl)
  {
    IntSetting videoBackend =
      new IntSetting(SettingsFile.KEY_VIDEO_BACKEND_INDEX, Settings.SECTION_INI_CORE,
        getVideoBackendValue());

    SettingSection gfxSection = mSettings.getSection(Settings.SECTION_GFX_SETTINGS);
    Setting showFps = gfxSection.getSetting(SettingsFile.KEY_SHOW_FPS);
    Setting shaderCompilationMode = gfxSection.getSetting(SettingsFile.KEY_SHADER_COMPILATION_MODE);
    Setting waitForShaders = gfxSection.getSetting(SettingsFile.KEY_WAIT_FOR_SHADERS);
    Setting aspectRatio = gfxSection.getSetting(SettingsFile.KEY_ASPECT_RATIO);
    Setting displayScale = gfxSection.getSetting(SettingsFile.KEY_DISPLAY_SCALE);
    Setting backendMultithreading = gfxSection.getSetting(SettingsFile.KEY_BACKEND_MULTITHREADING);

    sl.add(new SingleChoiceSetting(SettingsFile.KEY_VIDEO_BACKEND_INDEX, Settings.SECTION_INI_CORE,
      R.string.video_backend, 0, R.array.videoBackendEntries,
      R.array.videoBackendValues, 0, videoBackend));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_SHOW_FPS, Settings.SECTION_GFX_SETTINGS,
      R.string.show_fps, R.string.show_fps_description, false, showFps));
    sl.add(new SingleChoiceSetting(SettingsFile.KEY_SHADER_COMPILATION_MODE,
      Settings.SECTION_GFX_SETTINGS, R.string.shader_compilation_mode,
      R.string.shader_compilation_mode_description, R.array.shaderCompilationModeEntries,
      R.array.shaderCompilationModeValues, 0, shaderCompilationMode));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_WAIT_FOR_SHADERS, Settings.SECTION_GFX_SETTINGS,
      R.string.wait_for_shaders, R.string.wait_for_shaders_description, false,
      waitForShaders));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_BACKEND_MULTITHREADING,
      Settings.SECTION_GFX_SETTINGS,
      R.string.backend_multithreading, R.string.backend_multithreading_description, true,
      backendMultithreading));
    sl.add(new SingleChoiceSetting(SettingsFile.KEY_ASPECT_RATIO, Settings.SECTION_GFX_SETTINGS,
      R.string.aspect_ratio, 0, R.array.aspectRatioEntries,
      R.array.aspectRatioValues, 0, aspectRatio));
    sl.add(new SliderSetting(SettingsFile.KEY_DISPLAY_SCALE, Settings.SECTION_GFX_SETTINGS,
      R.string.setting_display_scale, 0, 200, "%", 100, displayScale));
  }

  private void addEnhanceSettings(ArrayList<SettingsItem> sl)
  {
    SettingSection gfxSection = mSettings.getSection(Settings.SECTION_GFX_SETTINGS);
    SettingSection enhancementSection = mSettings.getSection(Settings.SECTION_GFX_ENHANCEMENTS);
    SettingSection hacksSection = mSettings.getSection(Settings.SECTION_GFX_HACKS);

    Setting resolution = gfxSection.getSetting(SettingsFile.KEY_INTERNAL_RES);
    Setting fsaa = gfxSection.getSetting(SettingsFile.KEY_FSAA);
    Setting anisotropic = enhancementSection.getSetting(SettingsFile.KEY_ANISOTROPY);
    Setting shader = enhancementSection.getSetting(SettingsFile.KEY_POST_SHADER);
    Setting efbScaledCopy = hacksSection.getSetting(SettingsFile.KEY_SCALED_EFB);
    Setting perPixel = gfxSection.getSetting(SettingsFile.KEY_PER_PIXEL);
    Setting forceFilter = enhancementSection.getSetting(SettingsFile.KEY_FORCE_FILTERING);
    Setting disableFog = gfxSection.getSetting(SettingsFile.KEY_DISABLE_FOG);
    Setting disableCopyFilter = enhancementSection.getSetting(SettingsFile.KEY_DISABLE_COPY_FILTER);
    Setting arbitraryMipmapDetection =
            enhancementSection.getSetting(SettingsFile.KEY_ARBITRARY_MIPMAP_DETECTION);
    Setting wideScreenHack = gfxSection.getSetting(SettingsFile.KEY_WIDE_SCREEN_HACK);
    Setting force24BitColor = enhancementSection.getSetting(SettingsFile.KEY_FORCE_24_BIT_COLOR);

    sl.add(new SingleChoiceSetting(SettingsFile.KEY_INTERNAL_RES, Settings.SECTION_GFX_SETTINGS,
      R.string.internal_resolution, R.string.internal_resolution_description,
      R.array.internalResolutionEntries, R.array.internalResolutionValues, 100, resolution));
    sl.add(new SingleChoiceSetting(SettingsFile.KEY_FSAA, Settings.SECTION_GFX_SETTINGS,
      R.string.FSAA, R.string.FSAA_description, R.array.FSAAEntries, R.array.FSAAValues, 1,
      fsaa));
    sl.add(new SingleChoiceSetting(SettingsFile.KEY_ANISOTROPY, Settings.SECTION_GFX_ENHANCEMENTS,
      R.string.anisotropic_filtering, R.string.anisotropic_filtering_description,
      R.array.anisotropicFilteringEntries, R.array.anisotropicFilteringValues, 0,
      anisotropic));

    String[] shaderListValues = getShadersValues();
    String[] shaderListEntries = getShaderEntries(shaderListValues);
    sl.add(new StringSingleChoiceSetting(SettingsFile.KEY_POST_SHADER,
      Settings.SECTION_GFX_ENHANCEMENTS, R.string.post_processing_shader,
      0, shaderListEntries, shaderListValues, "",
      shader));

    sl.add(new CheckBoxSetting(SettingsFile.KEY_SCALED_EFB, Settings.SECTION_GFX_HACKS,
      R.string.scaled_efb_copy, R.string.scaled_efb_copy_description, true, efbScaledCopy));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_PER_PIXEL, Settings.SECTION_GFX_SETTINGS,
      R.string.per_pixel_lighting, R.string.per_pixel_lighting_description, false, perPixel));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_FORCE_FILTERING, Settings.SECTION_GFX_ENHANCEMENTS,
      R.string.force_texture_filtering, R.string.force_texture_filtering_description, false,
      forceFilter));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_FORCE_24_BIT_COLOR, Settings.SECTION_GFX_ENHANCEMENTS,
      R.string.force_24bit_color, R.string.force_24bit_color_description, true,
      force24BitColor));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_DISABLE_FOG, Settings.SECTION_GFX_SETTINGS,
      R.string.disable_fog, R.string.disable_fog_description, false, disableFog));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_DISABLE_COPY_FILTER, Settings.SECTION_GFX_ENHANCEMENTS,
      R.string.disable_copy_filter, R.string.disable_copy_filter_description, false,
      disableCopyFilter));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_ARBITRARY_MIPMAP_DETECTION,
      Settings.SECTION_GFX_ENHANCEMENTS, R.string.arbitrary_mipmap_detection,
      R.string.arbitrary_mipmap_detection_description, true, arbitraryMipmapDetection));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_WIDE_SCREEN_HACK, Settings.SECTION_GFX_SETTINGS,
            R.string.wide_screen_hack, R.string.wide_screen_hack_description, false,
            wideScreenHack));
  }

  private String capitalize(String text)
  {
    if (text.contains("_"))
    {
      text = text.replace("_"," ");
    }

    if (text.length() > 1 && text.contains(" "))
    {
      String[] ss = text.split(" ");
      text = capitalize(ss[0]);
      for(int i = 1; i < ss.length; ++i)
      {
        text += " " + capitalize(ss[i]);
      }
      return text;
    }

    return text.substring(0, 1).toUpperCase() + text.substring(1);
  }

  private String[] getShaderEntries(String[] values)
  {
    String[] entries = new String[values.length];
    entries[0] = mActivity.getString(R.string.off);
    for(int i = 1; i < values.length; ++i)
    {
      entries[i] = capitalize(values[i]);
    }
    return entries;
  }

  private String[] getShadersValues()
  {
    List<String> values = new ArrayList<>();
    values.add("");

    String shadersPath = DirectoryInitialization.getInternalDirectory() + "/Shaders";
    File file = new File(shadersPath);
    File[] shaderFiles = file.listFiles();
    if (shaderFiles != null)
    {
      for (int i = 0; i < shaderFiles.length; ++i)
      {
        String name = shaderFiles[i].getName();
        int extensionIndex = name.indexOf(".glsl");
        if (extensionIndex > 0)
        {
          values.add(name.substring(0, extensionIndex));
        }
      }
    }

    shadersPath = DirectoryInitialization.getUserDirectory() + "/Shaders";
    file = new File(shadersPath);
    shaderFiles = file.listFiles();
    if (shaderFiles != null)
    {
      for (int i = 0; i < shaderFiles.length; ++i)
      {
        String name = shaderFiles[i].getName();
        int extensionIndex = name.indexOf(".glsl");
        if (extensionIndex > 0)
        {
          values.add(name.substring(0, extensionIndex));
        }
      }
    }

    return values.toArray(new String[0]);
  }

  private void addHackSettings(ArrayList<SettingsItem> sl)
  {
    SettingSection gfxSection = mSettings.getSection(Settings.SECTION_GFX_SETTINGS);
    SettingSection hacksSection = mSettings.getSection(Settings.SECTION_GFX_HACKS);

    Setting skipEFB = hacksSection.getSetting(SettingsFile.KEY_SKIP_EFB);
    Setting ignoreFormat = hacksSection.getSetting(SettingsFile.KEY_IGNORE_FORMAT);
    Setting efbToTexture = hacksSection.getSetting(SettingsFile.KEY_EFB_TEXTURE);
    Setting deferEfbCopies = hacksSection.getSetting(SettingsFile.KEY_DEFER_EFB_COPIES);
    Setting deferEfbInvalid = hacksSection.getSetting(SettingsFile.KEY_EFB_DEFER_INVALIDATION);
    Setting texCacheAccuracy = gfxSection.getSetting(SettingsFile.KEY_TEXCACHE_ACCURACY);
    Setting gpuTextureDecoding = gfxSection.getSetting(SettingsFile.KEY_GPU_TEXTURE_DECODING);
    Setting xfbToTexture = hacksSection.getSetting(SettingsFile.KEY_XFB_TEXTURE);
    Setting immediateXfb = hacksSection.getSetting(SettingsFile.KEY_IMMEDIATE_XFB);
    Setting skipDuplicateXfbs = hacksSection.getSetting(SettingsFile.KEY_SKIP_DUPLICATE_XFBS);
    Setting approxLogicOpWithBlending =
            hacksSection.getSetting(SettingsFile.KEY_APPROX_LOGIC_OP_WITH_BLENDING);
    Setting viSkip = hacksSection.getSetting(SettingsFile.KEY_VI_SKIP);
    Setting fastDepth = gfxSection.getSetting(SettingsFile.KEY_FAST_DEPTH);
    Setting tmemEmu = hacksSection.getSetting(SettingsFile.KEY_TMEM_CACHE_EMULATION);

    sl.add(new HeaderSetting(null, null, R.string.embedded_frame_buffer, 0));

    sl.add(new CheckBoxSetting(SettingsFile.KEY_SKIP_EFB, Settings.SECTION_GFX_HACKS,
      R.string.skip_efb_access, R.string.skip_efb_access_description, true, skipEFB));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_IGNORE_FORMAT, Settings.SECTION_GFX_HACKS,
      R.string.ignore_format_changes, R.string.ignore_format_changes_description, true,
      ignoreFormat));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_EFB_TEXTURE, Settings.SECTION_GFX_HACKS,
      R.string.efb_copy_method, R.string.efb_copy_method_description, true, efbToTexture));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_DEFER_EFB_COPIES, Settings.SECTION_GFX_HACKS,
	  R.string.defer_efb_copies, R.string.defer_efb_copies_description, true, deferEfbCopies));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_EFB_DEFER_INVALIDATION, Settings.SECTION_GFX_HACKS,
	  R.string.efb_defer_invalidation, R.string.efb_defer_invalidation_description, false, deferEfbInvalid));

    sl.add(new HeaderSetting(null, null, R.string.texture_cache, 0));
    sl.add(new SingleChoiceSetting(SettingsFile.KEY_TEXCACHE_ACCURACY,
      Settings.SECTION_GFX_SETTINGS, R.string.texture_cache_accuracy,
      R.string.texture_cache_accuracy_description, R.array.textureCacheAccuracyEntries,
      R.array.textureCacheAccuracyValues, 128, texCacheAccuracy));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_GPU_TEXTURE_DECODING, Settings.SECTION_GFX_SETTINGS,
      R.string.gpu_texture_decoding, R.string.gpu_texture_decoding_description, false, gpuTextureDecoding));

    sl.add(new HeaderSetting(null, null, R.string.external_frame_buffer, 0));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_XFB_TEXTURE, Settings.SECTION_GFX_HACKS,
      R.string.xfb_copy_method, R.string.xfb_copy_method_description, true, xfbToTexture));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_IMMEDIATE_XFB, Settings.SECTION_GFX_HACKS,
      R.string.immediate_xfb, R.string.immediate_xfb_description, false, immediateXfb));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_SKIP_DUPLICATE_XFBS, Settings.SECTION_GFX_HACKS,
	  R.string.skip_duplicate_xfbs, R.string.skip_duplicate_xfbs_description, true,
	  skipDuplicateXfbs));

    sl.add(new HeaderSetting(null, null, R.string.other, 0));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_APPROX_LOGIC_OP_WITH_BLENDING, Settings.SECTION_GFX_HACKS,
      R.string.approx_logic_op_with_blending, R.string.approx_logic_op_with_blending_description,
      false, approxLogicOpWithBlending));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_VI_SKIP, Settings.SECTION_GFX_HACKS,
      R.string.vi_skip, R.string.vi_skip_description, false, viSkip));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_FAST_DEPTH, Settings.SECTION_GFX_SETTINGS,
      R.string.fast_depth_calculation, R.string.fast_depth_calculation_description, true, fastDepth));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_TMEM_CACHE_EMULATION, Settings.SECTION_GFX_HACKS,
      R.string.tmem_cache_emulation, R.string.tmem_cache_emulation_description, true, tmemEmu));
  }

  private void addDebugSettings(ArrayList<SettingsItem> sl)
  {
    SettingSection debugSection = mSettings.getSection(Settings.SECTION_DEBUG);

    Setting jitOff = debugSection.getSetting(SettingsFile.KEY_DEBUG_JITOFF);
    Setting jitLoadStoreOff = debugSection.getSetting(SettingsFile.KEY_DEBUG_JITLOADSTOREOFF);
    Setting jitLoadStoreFloatingPointOff =
            debugSection.getSetting(SettingsFile.KEY_DEBUG_JITLOADSTOREFLOATINGPOINTOFF);
    Setting jitLoadStorePairedOff =
            debugSection.getSetting(SettingsFile.KEY_DEBUG_JITLOADSTOREPAIREDOFF);
    Setting jitFloatingPointOff =
            debugSection.getSetting(SettingsFile.KEY_DEBUG_JITFLOATINGPOINTOFF);
    Setting jitIntegerOff = debugSection.getSetting(SettingsFile.KEY_DEBUG_JITINTEGEROFF);
    Setting jitPairedOff = debugSection.getSetting(SettingsFile.KEY_DEBUG_JITPAIREDOFF);
    Setting jitSystemRegistersOff =
            debugSection.getSetting(SettingsFile.KEY_DEBUG_JITSYSTEMREGISTEROFF);
    Setting jitBranchOff = debugSection.getSetting(SettingsFile.KEY_DEBUG_JITBRANCHOFF);
    Setting jitRegisterCacheOff =
            debugSection.getSetting(SettingsFile.KEY_DEBUG_JITREGISTERCACHEOFF);

    sl.add(new HeaderSetting(null, null, R.string.debug_warning, 0));

    sl.add(new CheckBoxSetting(SettingsFile.KEY_DEBUG_JITOFF, Settings.SECTION_DEBUG,
            R.string.debug_jitoff, 0, false,
            jitOff));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_DEBUG_JITLOADSTOREOFF, Settings.SECTION_DEBUG,
            R.string.debug_jitloadstoreoff, 0, false,
            jitLoadStoreOff));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_DEBUG_JITLOADSTOREFLOATINGPOINTOFF,
            Settings.SECTION_DEBUG,
            R.string.debug_jitloadstorefloatingoff, 0, false,
            jitLoadStoreFloatingPointOff));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_DEBUG_JITLOADSTOREPAIREDOFF, Settings.SECTION_DEBUG,
            R.string.debug_jitloadstorepairedoff, 0, false,
            jitLoadStorePairedOff));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_DEBUG_JITFLOATINGPOINTOFF, Settings.SECTION_DEBUG,
            R.string.debug_jitfloatingpointoff, 0, false,
            jitFloatingPointOff));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_DEBUG_JITINTEGEROFF, Settings.SECTION_DEBUG,
            R.string.debug_jitintegeroff, 0, false,
            jitIntegerOff));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_DEBUG_JITPAIREDOFF, Settings.SECTION_DEBUG,
            R.string.debug_jitpairedoff, 0, false,
            jitPairedOff));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_DEBUG_JITSYSTEMREGISTEROFF, Settings.SECTION_DEBUG,
            R.string.debug_jitsystemregistersoffr, 0, false,
            jitSystemRegistersOff));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_DEBUG_JITBRANCHOFF, Settings.SECTION_DEBUG,
            R.string.debug_jitbranchoff, 0, false,
            jitBranchOff));
    sl.add(new CheckBoxSetting(SettingsFile.KEY_DEBUG_JITREGISTERCACHEOFF, Settings.SECTION_DEBUG,
            R.string.debug_jitregistercacheoff, 0, false,
            jitRegisterCacheOff));
  }

  private void addGcPadSubSettings(ArrayList<SettingsItem> sl, int gcPadNumber, int gcPadType)
  {
    SettingSection bindingsSection = mSettings.getSection(Settings.SECTION_BINDINGS);
    SettingSection coreSection = mSettings.getSection(Settings.SECTION_INI_CORE);

    if (gcPadType == 1) // Emulated
    {
      Setting bindA = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_A + gcPadNumber);
      Setting bindB = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_B + gcPadNumber);
      Setting bindX = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_X + gcPadNumber);
      Setting bindY = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_Y + gcPadNumber);
      Setting bindZ = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_Z + gcPadNumber);
      Setting bindStart = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_START + gcPadNumber);
      Setting bindControlUp =
        bindingsSection.getSetting(SettingsFile.KEY_GCBIND_CONTROL_UP + gcPadNumber);
      Setting bindControlDown =
        bindingsSection.getSetting(SettingsFile.KEY_GCBIND_CONTROL_DOWN + gcPadNumber);
      Setting bindControlLeft =
        bindingsSection.getSetting(SettingsFile.KEY_GCBIND_CONTROL_LEFT + gcPadNumber);
      Setting bindControlRight =
        bindingsSection.getSetting(SettingsFile.KEY_GCBIND_CONTROL_RIGHT + gcPadNumber);
      Setting bindCUp = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_C_UP + gcPadNumber);
      Setting bindCDown = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_C_DOWN + gcPadNumber);
      Setting bindCLeft = bindingsSection.getSetting(SettingsFile.KEY_GCBIND_C_LEFT + gcPadNumber);
      Setting bindCRight =
        bindingsSection.getSetting(SettingsFile.KEY_GCBIND_C_RIGHT + gcPadNumber);
      Setting bindTriggerL =
        bindingsSection.getSetting(SettingsFile.KEY_GCBIND_TRIGGER_L + gcPadNumber);
      Setting bindTriggerR =
        bindingsSection.getSetting(SettingsFile.KEY_GCBIND_TRIGGER_R + gcPadNumber);
      Setting bindTriggerLAnalog =
        bindingsSection.getSetting(SettingsFile.KEY_GCBIND_TRIGGER_L_ANALOG + gcPadNumber);
      Setting bindTriggerRAnalog =
        bindingsSection.getSetting(SettingsFile.KEY_GCBIND_TRIGGER_R_ANALOG + gcPadNumber);
      Setting bindDPadUp =
        bindingsSection.getSetting(SettingsFile.KEY_GCBIND_DPAD_UP + gcPadNumber);
      Setting bindDPadDown =
        bindingsSection.getSetting(SettingsFile.KEY_GCBIND_DPAD_DOWN + gcPadNumber);
      Setting bindDPadLeft =
        bindingsSection.getSetting(SettingsFile.KEY_GCBIND_DPAD_LEFT + gcPadNumber);
      Setting bindDPadRight =
              bindingsSection.getSetting(SettingsFile.KEY_GCBIND_DPAD_RIGHT + gcPadNumber);
      Setting gcEmuRumble =
              bindingsSection.getSetting(SettingsFile.KEY_EMU_RUMBLE + gcPadNumber);

      sl.add(new HeaderSetting(null, null, R.string.generic_buttons, 0));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_A + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.button_a, bindA));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_B + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.button_b, bindB));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_X + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.button_x, bindX));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_Y + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.button_y, bindY));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_Z + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.button_z, bindZ));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_START + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.button_start, bindStart));

      sl.add(new HeaderSetting(null, null, R.string.controller_control, 0));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_CONTROL_UP + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.generic_up, bindControlUp));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_CONTROL_DOWN + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.generic_down, bindControlDown));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_CONTROL_LEFT + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.generic_left, bindControlLeft));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_CONTROL_RIGHT + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.generic_right, bindControlRight));

      sl.add(new HeaderSetting(null, null, R.string.controller_c, 0));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_C_UP + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.generic_up, bindCUp));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_C_DOWN + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.generic_down, bindCDown));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_C_LEFT + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.generic_left, bindCLeft));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_C_RIGHT + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.generic_right, bindCRight));

      sl.add(new HeaderSetting(null, null, R.string.controller_trig, 0));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_TRIGGER_L + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.trigger_left, bindTriggerL));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_TRIGGER_R + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.trigger_right, bindTriggerR));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_TRIGGER_L_ANALOG + gcPadNumber,
        Settings.SECTION_BINDINGS, R.string.trigger_left_analog, bindTriggerLAnalog));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_TRIGGER_R_ANALOG + gcPadNumber,
        Settings.SECTION_BINDINGS, R.string.trigger_right_analog, bindTriggerRAnalog));

      sl.add(new HeaderSetting(null, null, R.string.controller_dpad, 0));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_DPAD_UP + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.generic_up, bindDPadUp));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_DPAD_DOWN + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.generic_down, bindDPadDown));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_DPAD_LEFT + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.generic_left, bindDPadLeft));
      sl.add(new InputBindingSetting(SettingsFile.KEY_GCBIND_DPAD_RIGHT + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.generic_right, bindDPadRight));


      sl.add(new HeaderSetting(null, null, R.string.emulation_control_rumble, 0));
      sl.add(new RumbleBindingSetting(SettingsFile.KEY_EMU_RUMBLE + gcPadNumber,
              Settings.SECTION_BINDINGS, R.string.emulation_control_rumble, gcEmuRumble));
    }
    else // Adapter
    {
      Setting rumble = coreSection.getSetting(SettingsFile.KEY_GCADAPTER_RUMBLE + gcPadNumber);
      Setting bongos = coreSection.getSetting(SettingsFile.KEY_GCADAPTER_BONGOS + gcPadNumber);

      sl.add(new CheckBoxSetting(SettingsFile.KEY_GCADAPTER_RUMBLE + gcPadNumber,
        Settings.SECTION_INI_CORE, R.string.gc_adapter_rumble,
        R.string.gc_adapter_rumble_description, false, rumble));
      sl.add(new CheckBoxSetting(SettingsFile.KEY_GCADAPTER_BONGOS + gcPadNumber,
        Settings.SECTION_INI_CORE, R.string.gc_adapter_bongos,
        R.string.gc_adapter_bongos_description, false, bongos));
    }
  }

  private void addWiimoteSubSettings(ArrayList<SettingsItem> sl, int wiimoteNumber)
  {
    SettingSection bindingsSection = mSettings.getSection(Settings.SECTION_BINDINGS);

    Setting bindA = bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_A + wiimoteNumber);
    Setting bindB = bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_B + wiimoteNumber);
    Setting bind1 = bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_1 + wiimoteNumber);
    Setting bind2 = bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_2 + wiimoteNumber);
    Setting bindMinus = bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_MINUS + wiimoteNumber);
    Setting bindPlus = bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_PLUS + wiimoteNumber);
    Setting bindHome = bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_HOME + wiimoteNumber);
    Setting bindIRUp = bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_IR_UP + wiimoteNumber);
    Setting bindIRDown =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_IR_DOWN + wiimoteNumber);
    Setting bindIRLeft =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_IR_LEFT + wiimoteNumber);
    Setting bindIRRight =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_IR_RIGHT + wiimoteNumber);
    Setting bindIRHide =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_IR_HIDE + wiimoteNumber);
    Setting bindSwingUp =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SWING_UP + wiimoteNumber);
    Setting bindSwingDown =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SWING_DOWN + wiimoteNumber);
    Setting bindSwingLeft =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SWING_LEFT + wiimoteNumber);
    Setting bindSwingRight =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SWING_RIGHT + wiimoteNumber);
    Setting bindSwingForward =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SWING_FORWARD + wiimoteNumber);
    Setting bindSwingBackward =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SWING_BACKWARD + wiimoteNumber);
    Setting bindTiltForward =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_TILT_FORWARD + wiimoteNumber);
    Setting bindTiltBackward =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_TILT_BACKWARD + wiimoteNumber);
    Setting bindTiltLeft =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_TILT_LEFT + wiimoteNumber);
    Setting bindTiltRight =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_TILT_RIGHT + wiimoteNumber);
    Setting bindTiltModifier =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_TILT_MODIFIER + wiimoteNumber);
    Setting bindShakeX =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SHAKE_X + wiimoteNumber);
    Setting bindShakeY =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SHAKE_Y + wiimoteNumber);
    Setting bindShakeZ =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_SHAKE_Z + wiimoteNumber);
    Setting bindDPadUp =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DPAD_UP + wiimoteNumber);
    Setting bindDPadDown =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DPAD_DOWN + wiimoteNumber);
    Setting bindDPadLeft =
      bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DPAD_LEFT + wiimoteNumber);
    Setting bindDPadRight =
            bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DPAD_RIGHT + wiimoteNumber);
    Setting bindSidewaysToggle =
            bindingsSection.getSetting(SettingsFile.KEY_HOTKEYS_SIDEWAYS_TOGGLE + wiimoteNumber);
    Setting bindUprightToggle =
            bindingsSection.getSetting(SettingsFile.KEY_HOTKEYS_UPRIGHT_TOGGLE + wiimoteNumber);
    Setting wiiEmuRumble =
            bindingsSection.getSetting(SettingsFile.KEY_EMU_RUMBLE + wiimoteNumber);

    // Bindings use controller numbers 4-7 (0-3 are GameCube), but the extension setting uses 1-4.
    // But game game specific extension settings are saved in their own profile. These profiles
    // do not have any way to specify the controller that is loaded outside of knowing the filename
    // of the profile that was loaded.
    IntSetting extension;
    if (TextUtils.isEmpty(mGameID))
    {
      extension = new IntSetting(SettingsFile.KEY_WIIMOTE_EXTENSION,
              Settings.SECTION_WIIMOTE + wiimoteNumber, getExtensionValue(wiimoteNumber - 3));
      sl.add(new SingleChoiceSetting(SettingsFile.KEY_WIIMOTE_EXTENSION,
              Settings.SECTION_WIIMOTE + (wiimoteNumber - 3), R.string.wiimote_extensions, 0,
              R.array.wiimoteExtensionsEntries, R.array.wiimoteExtensionsValues, 0,
              extension, MenuTag.getWiimoteExtensionMenuTag(wiimoteNumber)));
    }
    else
    {
      mSettings.loadWiimoteProfile(mGameID, String.valueOf(wiimoteNumber - 4));
      extension = new IntSetting(SettingsFile.KEY_WIIMOTE_EXTENSION + (wiimoteNumber - 4),
              Settings.SECTION_CONTROLS, getExtensionValue(wiimoteNumber - 4));
      sl.add(new SingleChoiceSetting(SettingsFile.KEY_WIIMOTE_EXTENSION + (wiimoteNumber - 4),
              Settings.SECTION_CONTROLS, R.string.wiimote_extensions, 0,
              R.array.wiimoteExtensionsEntries, R.array.wiimoteExtensionsValues, 0,
              extension, MenuTag.getWiimoteExtensionMenuTag(wiimoteNumber)));
    }

    sl.add(new HeaderSetting(null, null, R.string.generic_buttons, 0));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_A + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.button_a, bindA));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_B + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.button_b, bindB));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_1 + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.button_one, bind1));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_2 + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.button_two, bind2));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_MINUS + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.button_minus, bindMinus));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_PLUS + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.button_plus, bindPlus));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_HOME + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.button_home, bindHome));

    sl.add(new HeaderSetting(null, null, R.string.wiimote_ir, 0));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_IR_UP + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_up, bindIRUp));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_IR_DOWN + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_down, bindIRDown));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_IR_LEFT + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_left, bindIRLeft));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_IR_RIGHT + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_right, bindIRRight));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_IR_HIDE + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.ir_hide, bindIRHide));

    sl.add(new HeaderSetting(null, null, R.string.wiimote_swing, 0));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_SWING_UP + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_up, bindSwingUp));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_SWING_DOWN + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_down, bindSwingDown));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_SWING_LEFT + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_left, bindSwingLeft));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_SWING_RIGHT + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_right, bindSwingRight));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_SWING_FORWARD + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_forward, bindSwingForward));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_SWING_BACKWARD + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_backward, bindSwingBackward));

    sl.add(new HeaderSetting(null, null, R.string.wiimote_tilt, 0));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_TILT_FORWARD + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_forward, bindTiltForward));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_TILT_BACKWARD + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_backward, bindTiltBackward));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_TILT_LEFT + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_left, bindTiltLeft));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_TILT_RIGHT + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_right, bindTiltRight));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_TILT_MODIFIER + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.tilt_modifier, bindTiltModifier));

    sl.add(new HeaderSetting(null, null, R.string.wiimote_shake, 0));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_SHAKE_X + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.shake_x, bindShakeX));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_SHAKE_Y + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.shake_y, bindShakeY));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_SHAKE_Z + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.shake_z, bindShakeZ));

    sl.add(new HeaderSetting(null, null, R.string.controller_dpad, 0));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_DPAD_UP + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_up, bindDPadUp));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_DPAD_DOWN + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_down, bindDPadDown));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_DPAD_LEFT + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_left, bindDPadLeft));
    sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_DPAD_RIGHT + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.generic_right, bindDPadRight));

    sl.add(new HeaderSetting(null, null, R.string.wiimote_hotkeys, 0));
    sl.add(new InputBindingSetting(SettingsFile.KEY_HOTKEYS_SIDEWAYS_TOGGLE + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.hotkey_sideways_toggle, bindSidewaysToggle));
    sl.add(new InputBindingSetting(SettingsFile.KEY_HOTKEYS_UPRIGHT_TOGGLE + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.hotkey_upright_toggle, bindUprightToggle));

    sl.add(new HeaderSetting(null, null, R.string.emulation_control_rumble, 0));
    sl.add(new RumbleBindingSetting(SettingsFile.KEY_EMU_RUMBLE + wiimoteNumber,
            Settings.SECTION_BINDINGS, R.string.emulation_control_rumble, wiiEmuRumble));
  }

  private void addExtensionTypeSettings(ArrayList<SettingsItem> sl, int wiimoteNumber,
    int extentionType)
  {
    SettingSection bindingsSection = mSettings.getSection(Settings.SECTION_BINDINGS);

    switch (extentionType)
    {
      case 1: // Nunchuk
        Setting bindC =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_C + wiimoteNumber);
        Setting bindZ =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_Z + wiimoteNumber);
        Setting bindUp =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_UP + wiimoteNumber);
        Setting bindDown =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_DOWN + wiimoteNumber);
        Setting bindLeft =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_LEFT + wiimoteNumber);
        Setting bindRight =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_RIGHT + wiimoteNumber);
        Setting bindSwingUp = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_UP + wiimoteNumber);
        Setting bindSwingDown = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_DOWN + wiimoteNumber);
        Setting bindSwingLeft = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_LEFT + wiimoteNumber);
        Setting bindSwingRight = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_RIGHT + wiimoteNumber);
        Setting bindSwingForward = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_FORWARD + wiimoteNumber);
        Setting bindSwingBackward = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_BACKWARD + wiimoteNumber);
        Setting bindTiltForward = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_FORWARD + wiimoteNumber);
        Setting bindTiltBackward = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_BACKWARD + wiimoteNumber);
        Setting bindTiltLeft = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_LEFT + wiimoteNumber);
        Setting bindTiltRight = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_RIGHT + wiimoteNumber);
        Setting bindTiltModifier = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_MODIFIER + wiimoteNumber);
        Setting bindShakeX = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SHAKE_X + wiimoteNumber);
        Setting bindShakeY = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SHAKE_Y + wiimoteNumber);
        Setting bindShakeZ = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SHAKE_Z + wiimoteNumber);

        sl.add(new HeaderSetting(null, null, R.string.generic_buttons, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_C + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.nunchuk_button_c, bindC));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_Z + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_z, bindZ));

        sl.add(new HeaderSetting(null, null, R.string.generic_stick, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_UP + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_up, bindUp));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_DOWN + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_down, bindDown));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_left, bindLeft));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindRight));

        sl.add(new HeaderSetting(null, null, R.string.wiimote_swing, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_UP + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_up, bindSwingUp));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_DOWN + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_down, bindSwingDown));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_left, bindSwingLeft));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindSwingRight));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_FORWARD + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_forward, bindSwingForward));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_NUNCHUK_SWING_BACKWARD + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_backward, bindSwingBackward));

        sl.add(new HeaderSetting(null, null, R.string.wiimote_tilt, 0));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_FORWARD + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_forward, bindTiltForward));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_BACKWARD + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_backward, bindTiltBackward));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_left, bindTiltLeft));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindTiltRight));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_NUNCHUK_TILT_MODIFIER + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.tilt_modifier, bindTiltModifier));

        sl.add(new HeaderSetting(null, null, R.string.wiimote_shake, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SHAKE_X + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.shake_x, bindShakeX));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SHAKE_Y + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.shake_y, bindShakeY));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_NUNCHUK_SHAKE_Z + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.shake_z, bindShakeZ));
        break;
      case 2: // Classic
        Setting bindA =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_A + wiimoteNumber);
        Setting bindB =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_B + wiimoteNumber);
        Setting bindX =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_X + wiimoteNumber);
        Setting bindY =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_Y + wiimoteNumber);
        Setting bindZL =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_ZL + wiimoteNumber);
        Setting bindZR =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_ZR + wiimoteNumber);
        Setting bindMinus =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_MINUS + wiimoteNumber);
        Setting bindPlus =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_PLUS + wiimoteNumber);
        Setting bindHome =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_HOME + wiimoteNumber);
        Setting bindLeftUp = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_LEFT_UP + wiimoteNumber);
        Setting bindLeftDown = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_LEFT_DOWN + wiimoteNumber);
        Setting bindLeftLeft = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_LEFT_LEFT + wiimoteNumber);
        Setting bindLeftRight = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_LEFT_RIGHT + wiimoteNumber);
        Setting bindRightUp = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_RIGHT_UP + wiimoteNumber);
        Setting bindRightDown = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_RIGHT_DOWN + wiimoteNumber);
        Setting bindRightLeft = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_RIGHT_LEFT + wiimoteNumber);
        Setting bindRightRight = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_RIGHT_RIGHT + wiimoteNumber);
        Setting bindL = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_TRIGGER_L + wiimoteNumber);
        Setting bindR = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_TRIGGER_R + wiimoteNumber);
        Setting bindDpadUp = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_DPAD_UP + wiimoteNumber);
        Setting bindDpadDown = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_DPAD_DOWN + wiimoteNumber);
        Setting bindDpadLeft = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_DPAD_LEFT + wiimoteNumber);
        Setting bindDpadRight = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_CLASSIC_DPAD_RIGHT + wiimoteNumber);

        sl.add(new HeaderSetting(null, null, R.string.generic_buttons, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_A + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_a, bindA));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_B + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_b, bindB));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_X + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_x, bindX));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_Y + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_y, bindY));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_ZL + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.classic_button_zl, bindZL));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_ZR + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.classic_button_zr, bindZR));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_MINUS + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_minus, bindMinus));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_PLUS + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_plus, bindPlus));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_HOME + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_home, bindHome));

        sl.add(new HeaderSetting(null, null, R.string.classic_leftstick, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_LEFT_UP + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_up, bindLeftUp));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_LEFT_DOWN + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_down, bindLeftDown));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_LEFT_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_left, bindLeftLeft));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_LEFT_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindLeftRight));

        sl.add(new HeaderSetting(null, null, R.string.classic_rightstick, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_RIGHT_UP + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_up, bindRightUp));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_RIGHT_DOWN + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_down, bindRightDown));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_RIGHT_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_left, bindRightLeft));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_RIGHT_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindRightRight));

        sl.add(new HeaderSetting(null, null, R.string.controller_trig, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_TRIGGER_L + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.trigger_left, bindR));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_TRIGGER_R + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.trigger_right, bindL));

        sl.add(new HeaderSetting(null, null, R.string.controller_dpad, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_DPAD_UP + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_up, bindDpadUp));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_DPAD_DOWN + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_down, bindDpadDown));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_DPAD_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_left, bindDpadLeft));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_CLASSIC_DPAD_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindDpadRight));
        break;
      case 3: // Guitar
        Setting bindFretGreen = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_FRET_GREEN + wiimoteNumber);
        Setting bindFretRed = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_FRET_RED + wiimoteNumber);
        Setting bindFretYellow = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_FRET_YELLOW + wiimoteNumber);
        Setting bindFretBlue = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_FRET_BLUE + wiimoteNumber);
        Setting bindFretOrange = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_FRET_ORANGE + wiimoteNumber);
        Setting bindStrumUp = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_STRUM_UP + wiimoteNumber);
        Setting bindStrumDown = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_STRUM_DOWN + wiimoteNumber);
        Setting bindGuitarMinus =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_GUITAR_MINUS + wiimoteNumber);
        Setting bindGuitarPlus =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_GUITAR_PLUS + wiimoteNumber);
        Setting bindGuitarUp = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_STICK_UP + wiimoteNumber);
        Setting bindGuitarDown = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_STICK_DOWN + wiimoteNumber);
        Setting bindGuitarLeft = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_STICK_LEFT + wiimoteNumber);
        Setting bindGuitarRight = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_STICK_RIGHT + wiimoteNumber);
        Setting bindWhammyBar = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_GUITAR_WHAMMY_BAR + wiimoteNumber);

        sl.add(new HeaderSetting(null, null, R.string.guitar_frets, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_GUITAR_FRET_GREEN + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_green, bindFretGreen));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_GUITAR_FRET_RED + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_red, bindFretRed));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_GUITAR_FRET_YELLOW + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_yellow, bindFretYellow));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_GUITAR_FRET_BLUE + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_blue, bindFretBlue));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_GUITAR_FRET_ORANGE + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_orange, bindFretOrange));

        sl.add(new HeaderSetting(null, null, R.string.guitar_strum, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_GUITAR_STRUM_UP + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_up, bindStrumUp));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_GUITAR_STRUM_DOWN + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_down, bindStrumDown));

        sl.add(new HeaderSetting(null, null, R.string.generic_buttons, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_GUITAR_MINUS + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_minus, bindGuitarMinus));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_GUITAR_PLUS + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_plus, bindGuitarPlus));

        sl.add(new HeaderSetting(null, null, R.string.generic_stick, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_GUITAR_STICK_UP + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_up, bindGuitarUp));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_GUITAR_STICK_DOWN + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_down, bindGuitarDown));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_GUITAR_STICK_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_left, bindGuitarLeft));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_GUITAR_STICK_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindGuitarRight));

        sl.add(new HeaderSetting(null, null, R.string.guitar_whammy, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_GUITAR_WHAMMY_BAR + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindWhammyBar));
        break;
      case 4: // Drums
        Setting bindPadRed =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_RED + wiimoteNumber);
        Setting bindPadYellow = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_YELLOW + wiimoteNumber);
        Setting bindPadBlue =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_BLUE + wiimoteNumber);
        Setting bindPadGreen = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_GREEN + wiimoteNumber);
        Setting bindPadOrange = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_ORANGE + wiimoteNumber);
        Setting bindPadBass =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_BASS + wiimoteNumber);
        Setting bindDrumsUp =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DRUMS_STICK_UP + wiimoteNumber);
        Setting bindDrumsDown = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_DRUMS_STICK_DOWN + wiimoteNumber);
        Setting bindDrumsLeft = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_DRUMS_STICK_LEFT + wiimoteNumber);
        Setting bindDrumsRight = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_DRUMS_STICK_RIGHT + wiimoteNumber);
        Setting bindDrumsMinus =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DRUMS_MINUS + wiimoteNumber);
        Setting bindDrumsPlus =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_DRUMS_PLUS + wiimoteNumber);

        sl.add(new HeaderSetting(null, null, R.string.drums_pads, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_RED + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_red, bindPadRed));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_YELLOW + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_yellow, bindPadYellow));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_BLUE + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_blue, bindPadBlue));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_GREEN + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_green, bindPadGreen));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_ORANGE + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_orange, bindPadOrange));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_DRUMS_PAD_BASS + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.drums_pad_bass, bindPadBass));

        sl.add(new HeaderSetting(null, null, R.string.generic_stick, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_DRUMS_STICK_UP + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_up, bindDrumsUp));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_DRUMS_STICK_DOWN + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_down, bindDrumsDown));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_DRUMS_STICK_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_left, bindDrumsLeft));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_DRUMS_STICK_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindDrumsRight));

        sl.add(new HeaderSetting(null, null, R.string.generic_buttons, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_DRUMS_MINUS + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_minus, bindDrumsMinus));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_DRUMS_PLUS + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_plus, bindDrumsPlus));
        break;
      case 5: // Turntable
        Setting bindGreenLeft = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_GREEN_LEFT + wiimoteNumber);
        Setting bindRedLeft = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_RED_LEFT + wiimoteNumber);
        Setting bindBlueLeft = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_BLUE_LEFT + wiimoteNumber);
        Setting bindGreenRight = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_GREEN_RIGHT + wiimoteNumber);
        Setting bindRedRight = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_RED_RIGHT + wiimoteNumber);
        Setting bindBlueRight = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_BLUE_RIGHT + wiimoteNumber);
        Setting bindTurntableMinus = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_MINUS + wiimoteNumber);
        Setting bindTurntablePlus =
          bindingsSection.getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_PLUS + wiimoteNumber);
        Setting bindEuphoria = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_EUPHORIA + wiimoteNumber);
        Setting bindTurntableLeftLeft = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_LEFT_LEFT + wiimoteNumber);
        Setting bindTurntableLeftRight = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_LEFT_RIGHT + wiimoteNumber);
        Setting bindTurntableRightLeft = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_RIGHT_LEFT + wiimoteNumber);
        Setting bindTurntableRightRight = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_RIGHT_RIGHT + wiimoteNumber);
        Setting bindTurntableUp = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_STICK_UP + wiimoteNumber);
        Setting bindTurntableDown = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_STICK_DOWN + wiimoteNumber);
        Setting bindTurntableLeft = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_STICK_LEFT + wiimoteNumber);
        Setting bindTurntableRight = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_STICK_RIGHT + wiimoteNumber);
        Setting bindEffectDial = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_EFFECT_DIAL + wiimoteNumber);
        Setting bindCrossfadeLeft = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_CROSSFADE_LEFT + wiimoteNumber);
        Setting bindCrossfadeRight = bindingsSection
          .getSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_CROSSFADE_RIGHT + wiimoteNumber);

        sl.add(new HeaderSetting(null, null, R.string.generic_buttons, 0));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TURNTABLE_GREEN_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.turntable_button_green_left, bindGreenLeft));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_RED_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.turntable_button_red_left, bindRedLeft));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_BLUE_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.turntable_button_blue_left, bindBlueLeft));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TURNTABLE_GREEN_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.turntable_button_green_right, bindGreenRight));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_RED_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.turntable_button_red_right, bindRedRight));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TURNTABLE_BLUE_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.turntable_button_blue_right, bindBlueRight));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_MINUS + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_minus, bindTurntableMinus));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_PLUS + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.button_plus, bindTurntablePlus));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_EUPHORIA + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.turntable_button_euphoria, bindEuphoria));

        sl.add(new HeaderSetting(null, null, R.string.turntable_table_left, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_LEFT_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_left, bindTurntableLeftLeft));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TURNTABLE_LEFT_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindTurntableLeftRight));

        sl.add(new HeaderSetting(null, null, R.string.turntable_table_right, 0));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TURNTABLE_RIGHT_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_left, bindTurntableRightLeft));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TURNTABLE_RIGHT_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindTurntableRightRight));

        sl.add(new HeaderSetting(null, null, R.string.generic_stick, 0));
        sl.add(new InputBindingSetting(SettingsFile.KEY_WIIBIND_TURNTABLE_STICK_UP + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_up, bindTurntableUp));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TURNTABLE_STICK_DOWN + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_down, bindTurntableDown));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TURNTABLE_STICK_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_left, bindTurntableLeft));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TURNTABLE_STICK_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindTurntableRight));

        sl.add(new HeaderSetting(null, null, R.string.turntable_effect, 0));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TURNTABLE_EFFECT_DIAL + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.turntable_effect_dial, bindEffectDial));

        sl.add(new HeaderSetting(null, null, R.string.turntable_crossfade, 0));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TURNTABLE_CROSSFADE_LEFT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_left, bindCrossfadeLeft));
        sl.add(new InputBindingSetting(
                SettingsFile.KEY_WIIBIND_TURNTABLE_CROSSFADE_RIGHT + wiimoteNumber,
                Settings.SECTION_BINDINGS, R.string.generic_right, bindCrossfadeRight));
        break;
    }
  }

  private int getVideoBackendValue()
  {
    SettingSection coreSection = mSettings.getSection(Settings.SECTION_INI_CORE);

    int videoBackendValue;

    try
    {
      String videoBackend =
        ((StringSetting) coreSection.getSetting(SettingsFile.KEY_VIDEO_BACKEND)).getValue();
      if (videoBackend.equals("OGL"))
      {
        videoBackendValue = 0;
      }
      else if (videoBackend.equals("Vulkan"))
      {
        videoBackendValue = 1;
      }
      else if (videoBackend.equals("Software Renderer"))
      {
        videoBackendValue = 2;
      }
      else if (videoBackend.equals("Null"))
      {
        videoBackendValue = 3;
      }
      else
      {
        videoBackendValue = 0;
      }
    }
    catch (NullPointerException ex)
    {
      videoBackendValue = 0;
    }

    return videoBackendValue;
  }

  private int getExtensionValue(int wiimoteNumber)
  {
    int extensionValue;

    try
    {
      String extension;
      if (TextUtils.isEmpty(mGameID)) // Main settings
      {
        extension =
                ((StringSetting) mSettings.getSection(Settings.SECTION_WIIMOTE + wiimoteNumber)
                        .getSetting(SettingsFile.KEY_WIIMOTE_EXTENSION)).getValue();
      }
      else // Game settings
      {
        extension = ((StringSetting) mSettings.getSection(Settings.SECTION_PROFILE)
                .getSetting(SettingsFile.KEY_WIIMOTE_EXTENSION)).getValue();
      }

      if (extension.equals("None"))
      {
        extensionValue = 0;
      }
      else if (extension.equals("Nunchuk"))
      {
        extensionValue = 1;
      }
      else if (extension.equals("Classic"))
      {
        extensionValue = 2;
      }
      else if (extension.equals("Guitar"))
      {
        extensionValue = 3;
      }
      else if (extension.equals("Drums"))
      {
        extensionValue = 4;
      }
      else if (extension.equals("Turntable"))
      {
        extensionValue = 5;
      }
      else
      {
        extensionValue = 0;
      }
    }
    catch (NullPointerException ex)
    {
      extensionValue = 0;
    }

    return extensionValue;
  }
}
