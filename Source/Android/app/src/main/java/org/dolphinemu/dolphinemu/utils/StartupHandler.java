package org.dolphinemu.dolphinemu.utils;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;

import org.dolphinemu.dolphinemu.NativeLibrary;
import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.activities.EmulationActivity;

public final class StartupHandler
{
  public static void HandleInit(Activity parent)
  {
    // to evade re-skins, the app will check package and app name
    // using C++. If they are not the same, the app will crash.
    // if you are a real programmer, you want make a fork or help
    // and you need change package or name for some reason,
    // just edit it on MainAndroid.cpp (JNI) :)
    if (!NativeLibrary.CheckIntegrity(parent.getPackageName(),
      parent.getResources().getString(R.string.app_name)))
    {
      Object obj = null;
      obj.toString();
    }

    // Ask the user to grant write permission if it's not already granted
    PermissionsHandler.checkWritePermission(parent);
		// Ask the user if he wants to check for updates at startup if we haven't yet.
		// If allowed, check for updates.
		UpdaterUtils.checkUpdatesInit(parent);
		String[] start_files = null;
    Bundle extras = parent.getIntent().getExtras();
    if (extras != null)
    {
      start_files = extras.getStringArray("AutoStartFiles");
      if (start_files == null)
      {
        String start_file = extras.getString("AutoStartFile");
        if (!TextUtils.isEmpty(start_file))
        {
          start_files = new String[]{start_file};
        }
      }
    }

    if (start_files != null && start_files.length > 0)
    {
      // Start the emulation activity, send the ISO passed in and finish the main activity
      EmulationActivity.launchFile(parent, start_files);
      parent.finish();
    }
  }
}
