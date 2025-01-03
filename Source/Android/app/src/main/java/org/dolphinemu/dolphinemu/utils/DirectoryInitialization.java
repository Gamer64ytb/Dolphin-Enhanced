/**
 * Copyright 2014 Dolphin Emulator Project
 * Licensed under GPLv2+
 * Refer to the license.txt file included.
 */

package org.dolphinemu.dolphinemu.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.dolphinemu.dolphinemu.BuildConfig;
import org.dolphinemu.dolphinemu.NativeLibrary;
import org.dolphinemu.dolphinemu.overlay.InputOverlay;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * A service that spawns its own thread in order to copy several binary and shader files
 * from the Dolphin APK to the external file system.
 */
public final class DirectoryInitialization
{
  public static final String BROADCAST_ACTION =
    "org.dolphinemu.dolphinemu.DIRECTORY_INITIALIZATION";

  public static final String EXTRA_STATE = "directoryState";
  private static final Integer WiimoteNewVersion = 2;
  private static volatile DirectoryInitializationState mDirectoryState;
  private static String mUserPath;
  private static String mInternalPath;
  private static String mDriverPath;
  private static AtomicBoolean mIsRunning = new AtomicBoolean(false);

  public enum DirectoryInitializationState
  {
    DIRECTORIES_INITIALIZED,
    EXTERNAL_STORAGE_PERMISSION_NEEDED,
    CANT_FIND_EXTERNAL_STORAGE
  }

  public static void start(Context context)
  {
    // Can take a few seconds to run, so don't block UI thread.
    //noinspection TrivialFunctionalExpressionUsage
    ((Runnable) () -> init(context)).run();
  }

  private static class InitTask extends AsyncTask<Context, Void, Void> {
    @Override
    protected Void doInBackground(Context... contexts) {
      initializeExternalStorage(contexts[0]);
      return null;
    }
  }

  private static void init(Context context)
  {
    if (!mIsRunning.compareAndSet(false, true))
      return;

    if (mDirectoryState != DirectoryInitializationState.DIRECTORIES_INITIALIZED)
    {
      if (PermissionsHandler.hasWriteAccess(context))
      {
        if (setDolphinUserDirectory(context))
        {
          initializeInternalStorage(context);
          NativeLibrary.setSystemLanguage(Locale.getDefault().getLanguage());
          new InitTask().execute(context);
        }
        else
        {
          mDirectoryState = DirectoryInitializationState.CANT_FIND_EXTERNAL_STORAGE;
        }
      }
      else
      {
        mDirectoryState = DirectoryInitializationState.EXTERNAL_STORAGE_PERMISSION_NEEDED;
      }
    }

    mIsRunning.set(false);
    sendBroadcastState(mDirectoryState, context);
  }

  public static boolean isInitialized() {
    return mDirectoryState == DirectoryInitializationState.DIRECTORIES_INITIALIZED;
  }

  private static boolean setDolphinUserDirectory(Context context)
  {
    if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
      return false;

    File externalPath = Environment.getExternalStorageDirectory();
    if (externalPath == null)
      return false;

    File userPath = new File(externalPath, "dolphin-mmj");
    if (!userPath.isDirectory() && !userPath.mkdir())
    {
      return false;
    }
    mUserPath = userPath.getPath();
    NativeLibrary.SetUserDirectory(mUserPath);

    File cacheDir = context.getExternalCacheDir();
    if (cacheDir == null)
      return false;

    if (!cacheDir.isDirectory() && !cacheDir.mkdir())
    {
      return false;
    }
    NativeLibrary.SetCacheDirectory(cacheDir.getPath());
    return true;
  }

  private static void initializeInternalStorage(Context context)
  {
    File sysDirectory = new File(context.getFilesDir(), "Sys");
    mInternalPath = sysDirectory.getAbsolutePath();

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    String revision = String.valueOf(BuildConfig.VERSION_CODE);
    if (!preferences.getString("sysDirectoryVersion", "").equals(revision))
    {
      // There is no extracted Sys directory, or there is a Sys directory from another
      // version of Dolphin that might contain outdated files. Let's (re-)extract Sys.
      deleteDirectoryRecursively(sysDirectory);
      copyAssetFolder("Sys", sysDirectory, true, context);

      SharedPreferences.Editor editor = preferences.edit();
      editor.putString("sysDirectoryVersion", revision);
      editor.apply();
    }

    // Let the native code know where the Sys directory is.
    NativeLibrary.SetSysDirectory(sysDirectory.getPath());

    File driverDirectory = new File(context.getFilesDir(), "GPUDrivers");
    driverDirectory.mkdirs();
    File driverExtractedDir = new File(driverDirectory, "Extracted");
    driverExtractedDir.mkdirs();
    File driverTmpDir = new File(driverDirectory, "Tmp");
    driverTmpDir.mkdirs();
    File driverFileRedirectDir = new File(driverDirectory, "FileRedirect");
    driverFileRedirectDir.mkdirs();

    SetGpuDriverDirectories(driverDirectory.getPath(),
            context.getApplicationInfo().nativeLibraryDir);
    mDriverPath = driverExtractedDir.getAbsolutePath();
  }

  private static void initializeExternalStorage(Context context)
  {
    // Create User directory structure and copy some NAND files from the extracted Sys directory.
    NativeLibrary.CreateUserDirectories();

    mDirectoryState = DirectoryInitializationState.DIRECTORIES_INITIALIZED;

    // GCPadNew.ini and WiimoteNew.ini must contain specific values in order for controller
    // input to work as intended (they aren't user configurable), so we overwrite them just
    // in case the user has tried to modify them manually.
    //
    // ...Except WiimoteNew.ini contains the user configurable settings for Wii Remote
    // extensions in addition to all of its lines that aren't user configurable, so since we
    // don't want to lose the selected extensions, we don't overwrite that file if it exists.
    //
    // TODO: Redo the Android controller system so that we don't have to extract these INIs.
    String configDirectory = NativeLibrary.GetUserDirectory() + File.separator + "Config";
    String profileDirectory =
            NativeLibrary.GetUserDirectory() + File.separator + "Config/Profiles/Wiimote/";
    createWiimoteProfileDirectory(profileDirectory);

    copyAsset("GCPadNew.ini", new File(configDirectory, "GCPadNew.ini"), true, context);

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    if (prefs.getInt("WiimoteNewVersion", 0) != WiimoteNewVersion)
    {
      copyAsset("WiimoteNew.ini", new File(configDirectory, "WiimoteNew.ini"), true, context);
      SharedPreferences.Editor sPrefsEditor = prefs.edit();
      sPrefsEditor.putInt("WiimoteNewVersion", WiimoteNewVersion);
      sPrefsEditor.apply();
    }
    else
    {
      copyAsset("WiimoteNew.ini", new File(configDirectory, "WiimoteNew.ini"), false, context);
    }

    copyAsset("WiimoteProfile.ini", new File(profileDirectory, "WiimoteProfile.ini"), true,
            context);

    File theme = new File(getThemeDirectory());

    if (theme.exists() || theme.mkdir()) {
      saveInputOverlay(context);
    }
  }

  private static void deleteDirectoryRecursively(File file)
  {
    if (file.isDirectory())
    {
      for (File child : file.listFiles())
        deleteDirectoryRecursively(child);
    }
    file.delete();
  }

  public static boolean isReady()
  {
    return mDirectoryState == DirectoryInitializationState.DIRECTORIES_INITIALIZED;
  }

  public static String getUserDirectory()
  {
    if (mDirectoryState == null)
    {
      throw new IllegalStateException("DirectoryInitialization has to run at least once!");
    }
    else if (mIsRunning.get())
    {
      throw new IllegalStateException("DirectoryInitialization has to finish running first!");
    }
    return mUserPath;
  }

  public static String getExtractedDriverDirectory()
  {
    if (mDirectoryState == null)
    {
      throw new IllegalStateException("DirectoryInitialization has to run at least once!");
    }
    else if (mIsRunning.get())
    {
      throw new IllegalStateException("DirectoryInitialization has to finish running first!");
    }
    return mDriverPath;
  }

  public static String getCacheDirectory(Context context)
  {
    return context.getExternalCacheDir().getPath();
  }

  public static String getLocalSettingFile(String gameId)
  {
    return getUserDirectory() + File.separator + "GameSettings" + File.separator + gameId +
      ".ini";
  }

  public static String getThemeDirectory() {
    return getUserDirectory() + File.separator + "Theme";
  }

  public static String getInternalDirectory()
  {
    if (mDirectoryState == null)
    {
      throw new IllegalStateException("DirectoryInitialization has to run at least once!");
    }
    else if (mIsRunning.get())
    {
      throw new IllegalStateException("DirectoryInitialization has to finish running first!");
    }
    return mInternalPath;
  }

  public static void saveInputOverlay(Context context) {
    final int[] gcInputIds = InputOverlay.ResGameCubeIds;
    final int[] dpadAndJoystickInputIds = InputOverlay.ResDpadAndJoystickIds;
    final int[] wiimoteInputIds = InputOverlay.ResWiimoteIds;
    final int[] classicInputIds = InputOverlay.ResClassicIds;
    final String[] gcInputNames = InputOverlay.ResGameCubeNames;
    final String[] dpadAndJoystickInputNames = InputOverlay.ResDpadAndJoystickNames;
    final String[] wiimoteInputNames = InputOverlay.ResWiimoteNames;
    final String[] classicInputNames = InputOverlay.ResClassicNames;
    String gcPath = getThemeDirectory() + "/gcDefault.zip";
    String dpadAndJoystickPath = getThemeDirectory() + "/dpadJoystickDefault.zip";
    String wiimotePath = getThemeDirectory() + "/wiimoteDefault.zip";
    String classicPath = getThemeDirectory() + "/classicDefault.zip";
    File gcFile = new File(gcPath);
    File dpadJoystickFile = new File(dpadAndJoystickPath);
    File wiimoteFile = new File(wiimotePath);
    File classicFile = new File(classicPath);
    if (gcFile.exists() || dpadJoystickFile.exists() || wiimoteFile.exists() || classicFile.exists()) {
      return;
    }
    try {
      FileOutputStream gcOutputStream = new FileOutputStream(gcFile);
      FileOutputStream dpadJoystickOutputStream = new FileOutputStream(dpadJoystickFile);
      FileOutputStream wiimoteOutputStream = new FileOutputStream(wiimoteFile);
      FileOutputStream classicOutputStream = new FileOutputStream(classicFile);
      ZipOutputStream gcZipOut = new ZipOutputStream(new BufferedOutputStream(gcOutputStream));
      ZipOutputStream dpadJoystickZipOut = new ZipOutputStream(new BufferedOutputStream(dpadJoystickOutputStream));
      ZipOutputStream wiimoteZipOut = new ZipOutputStream(new BufferedOutputStream(wiimoteOutputStream));
      ZipOutputStream classicZipOut = new ZipOutputStream(new BufferedOutputStream(classicOutputStream));
      for (int i = 0; i < gcInputIds.length; ++i) {
        ZipEntry entry = new ZipEntry(gcInputNames[i]);
        gcZipOut.putNextEntry(entry);
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), gcInputIds[i]);
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, gcZipOut);
      }
      gcZipOut.close();
      for (int i = 0; i < dpadAndJoystickInputIds.length; ++i) {
        ZipEntry entry = new ZipEntry(dpadAndJoystickInputNames[i]);
        dpadJoystickZipOut.putNextEntry(entry);
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), dpadAndJoystickInputIds[i]);
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, dpadJoystickZipOut);
      }
      dpadJoystickZipOut.close();
      for (int i = 0; i < wiimoteInputIds.length; ++i) {
        ZipEntry entry = new ZipEntry(wiimoteInputNames[i]);
        wiimoteZipOut.putNextEntry(entry);
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), wiimoteInputIds[i]);
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, wiimoteZipOut);
      }
      wiimoteZipOut.close();
      for (int i = 0; i < classicInputIds.length; ++i) {
        ZipEntry entry = new ZipEntry(classicInputNames[i]);
        classicZipOut.putNextEntry(entry);
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), classicInputIds[i]);
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, classicZipOut);
      }
      classicZipOut.close();
    } catch (IOException e) {
      // ignore
    }
  }

  public static Map<Integer, Bitmap> loadInputOverlay(Context context) {
    final int[] gcInputIds = InputOverlay.ResGameCubeIds;
    final int[] dpadAndJoystickInputIds = InputOverlay.ResDpadAndJoystickIds;
    final int[] wiimoteInputIds = InputOverlay.ResWiimoteIds;
    final int[] classicInputIds = InputOverlay.ResClassicIds;
    final String[] gcInputNames = InputOverlay.ResGameCubeNames;
    final String[] dpadAndJoystickInputNames = InputOverlay.ResDpadAndJoystickNames;
    final String[] wiimoteInputNames = InputOverlay.ResWiimoteNames;
    final String[] classicInputNames = InputOverlay.ResClassicNames;
    Map<Integer, Bitmap> inputs = new HashMap<>();
    String gcPath = getThemeDirectory() + "/gcDefault.zip";
    String dpadAndJoystickPath = getThemeDirectory() + "/dpadJoystickDefault.zip";
    String wiimotePath = getThemeDirectory() + "/wiimoteDefault.zip";
    String classicPath = getThemeDirectory() + "/classicDefault.zip";
    File gcFile = new File(gcPath);
    File dpadJoystickFile = new File(dpadAndJoystickPath);
    File wiimoteFile = new File(wiimotePath);
    File classicFile = new File(classicPath);

    // default bitmaps
    for (int id : gcInputIds) {
      Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), id);
      inputs.put(id, bitmap);
    }
    for (int id : dpadAndJoystickInputIds) {
      Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), id);
      inputs.put(id, bitmap);
    }
    for (int id : wiimoteInputIds) {
      Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), id);
      inputs.put(id, bitmap);
    }
    for (int id : classicInputIds) {
      Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), id);
      inputs.put(id, bitmap);
    }

    if (gcFile.exists() || dpadJoystickFile.exists() || wiimoteFile.exists() || classicFile.exists()) {
      return inputs;
    }

    try {
      FileInputStream gcInputStream = new FileInputStream(gcFile);
      FileInputStream dpadJoystickInputStream = new FileInputStream(dpadJoystickFile);
      FileInputStream wiimoteInputStream = new FileInputStream(wiimoteFile);
      FileInputStream classicInputStream = new FileInputStream(classicFile);
      ZipInputStream gcZipIn = new ZipInputStream(new BufferedInputStream(gcInputStream));
      ZipInputStream dpadJoystickZipIn = new ZipInputStream(new BufferedInputStream(dpadJoystickInputStream));
      ZipInputStream wiimoteZipIn = new ZipInputStream(new BufferedInputStream(wiimoteInputStream));
      ZipInputStream classicZipIn = new ZipInputStream(new BufferedInputStream(classicInputStream));

      ZipEntry entry;
      while ((entry = gcZipIn.getNextEntry()) != null) {
        for (int i = 0; i < gcInputNames.length; ++i) {
          if (!entry.isDirectory() && gcInputNames[i].equals(entry.getName())) {
            Bitmap bitmap = BitmapFactory.decodeStream(gcZipIn);
            inputs.put(gcInputIds[i], bitmap);
          }
        }
      }
      gcZipIn.close();
      while ((entry = dpadJoystickZipIn.getNextEntry()) != null) {
        for (int i = 0; i < dpadAndJoystickInputNames.length; ++i) {
          if (!entry.isDirectory() && dpadAndJoystickInputNames[i].equals(entry.getName())) {
            Bitmap bitmap = BitmapFactory.decodeStream(dpadJoystickZipIn);
            inputs.put(dpadAndJoystickInputIds[i], bitmap);
          }
        }
      }
      dpadJoystickZipIn.close();
      while ((entry = wiimoteZipIn.getNextEntry()) != null) {
        for (int i = 0; i < wiimoteInputNames.length; ++i) {
          if (!entry.isDirectory() && wiimoteInputNames[i].equals(entry.getName())) {
            Bitmap bitmap = BitmapFactory.decodeStream(wiimoteZipIn);
            inputs.put(wiimoteInputIds[i], bitmap);
          }
        }
      }
      wiimoteZipIn.close();
      while ((entry = classicZipIn.getNextEntry()) != null) {
        for (int i = 0; i < classicInputNames.length; ++i) {
          if (!entry.isDirectory() && classicInputNames[i].equals(entry.getName())) {
            Bitmap bitmap = BitmapFactory.decodeStream(classicZipIn);
            inputs.put(classicInputIds[i], bitmap);
          }
        }
      }
      classicZipIn.close();
    } catch (IOException e) {
      // ignore
    }

    return inputs;
  }

  private static void sendBroadcastState(DirectoryInitializationState state, Context context)
  {
    Intent localIntent = new Intent(BROADCAST_ACTION).putExtra(EXTRA_STATE, state);
    LocalBroadcastManager.getInstance(context).sendBroadcast(localIntent);
  }

  private static void copyAsset(String asset, File output, Boolean overwrite, Context context)
  {
    Log.verbose("[DirectoryInitialization] Copying File " + asset + " to " + output);

    try
    {
      if (!output.exists() || overwrite)
      {
        InputStream in = context.getAssets().open(asset);
        OutputStream out = new FileOutputStream(output);
        copyFile(in, out);
        in.close();
        out.close();
      }
    }
    catch (IOException e)
    {
      Log.error("[DirectoryInitialization] Failed to copy asset file: " + asset +
        e.getMessage());
    }
  }

  private static void copyAssetFolder(String assetFolder, File outputFolder, Boolean overwrite,
    Context context)
  {
    Log.verbose("[DirectoryInitialization] Copying Folder " + assetFolder + " to " + outputFolder);

    try
    {
      boolean createdFolder = false;
      for (String file : context.getAssets().list(assetFolder))
      {
        if (!createdFolder)
        {
          outputFolder.mkdir();
          createdFolder = true;
        }
        copyAssetFolder(assetFolder + File.separator + file, new File(outputFolder, file),
          overwrite, context);
        copyAsset(assetFolder + File.separator + file, new File(outputFolder, file), overwrite,
          context);
      }
    }
    catch (IOException e)
    {
      Log.error("[DirectoryInitialization] Failed to copy asset folder: " + assetFolder +
        e.getMessage());
    }
  }

  public static void copyFile(String from, String to)
  {
    try
    {
      InputStream in = new FileInputStream(from);
      OutputStream out = new FileOutputStream(to);
      copyFile(in, out);
    }
    catch (IOException e)
    {

    }
  }

  private static void copyFile(InputStream in, OutputStream out) throws IOException
  {
    byte[] buffer = new byte[4096];
    int read;

    while ((read = in.read(buffer)) != -1)
    {
      out.write(buffer, 0, read);
    }
  }

  private static void createWiimoteProfileDirectory(String directory)
  {
    File wiiPath = new File(directory);
    if (!wiiPath.isDirectory())
    {
      wiiPath.mkdirs();
    }
  }

  private static native void SetGpuDriverDirectories(String path, String libPath);
}
