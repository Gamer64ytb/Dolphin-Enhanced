package org.dolphinemu.dolphinemu.utils;

import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

import org.dolphinemu.dolphinemu.features.settings.model.Setting;
import org.dolphinemu.dolphinemu.features.settings.model.Settings;
import org.dolphinemu.dolphinemu.features.settings.utils.SettingsFile;

public class ThemeUtil
{
  public static void applyTheme()
  {
    Settings settings = new Settings();

    try
    {
      settings.loadSettings(null);
      applyTheme(settings);
    }
    catch (Exception e)
    {
      applyTheme(2);
    }
  }

  private static void applyTheme(int designValue)
  {
    switch (designValue)
    {
      case 0:
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        break;
      case 1:
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        break;
      case 2:
        AppCompatDelegate.setDefaultNightMode(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM :
        AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
        break;
    }
  }

  public static void applyTheme(Settings settings)
  {
    Setting design = settings.getSection(Settings.SECTION_INI_INTERFACE).getSetting((SettingsFile.KEY_DESIGN));
    if (design == null)
    {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
      return;
    }

    applyTheme(Integer.parseInt(design.getValueAsString()));
  }
}
