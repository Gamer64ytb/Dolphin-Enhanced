package org.dolphinemu.dolphinemu.ui.main;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager.widget.ViewPager;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;

import org.dolphinemu.dolphinemu.adapters.PlatformPagerAdapter;

import org.dolphinemu.dolphinemu.NativeLibrary;
import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.activities.EmulationActivity;
import org.dolphinemu.dolphinemu.features.settings.ui.MenuTag;
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsActivity;
import org.dolphinemu.dolphinemu.model.GameFileCache;
import org.dolphinemu.dolphinemu.services.GameFileCacheService;
import org.dolphinemu.dolphinemu.ui.platform.Platform;
import org.dolphinemu.dolphinemu.ui.platform.PlatformGamesView;
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization;
import org.dolphinemu.dolphinemu.utils.FileBrowserHelper;
import org.dolphinemu.dolphinemu.utils.PermissionsHandler;
import org.dolphinemu.dolphinemu.utils.SafHandler;
import org.dolphinemu.dolphinemu.utils.StartupHandler;
import org.dolphinemu.dolphinemu.utils.UpdaterUtils;

import java.io.File;
import java.util.Arrays;

public final class MainActivity extends AppCompatActivity
{
  public static final int REQUEST_ADD_DIRECTORY = 1;
  public static final int REQUEST_OPEN_FILE = 2;
	public static final int REQUEST_INSTALL_WAD = 3;
  private static final byte[] TITLE_BYTES = {
    0x44, 0x6f, 0x6c, 0x70, 0x68, 0x69, 0x6e, 0x20, 0x35, 0x2e, 0x30, 0x28, 0x4d, 0x4d, 0x4a, 0x29};
	private ViewPager mViewPager;
  private Toolbar mToolbar;
	private TabLayout mTabLayout;
  private BroadcastReceiver mBroadcastReceiver;
  private String mDirToAdd;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_main);
    findViews();
    setSupportActionBar(mToolbar);

		mTabLayout.setupWithViewPager(mViewPager);
    setTitle(getString(R.string.app_name));

    IntentFilter filter = new IntentFilter();
    filter.addAction(GameFileCacheService.BROADCAST_ACTION);
    mBroadcastReceiver = new BroadcastReceiver()
    {
      @Override
      public void onReceive(Context context, Intent intent)
      {
        showGames();
      }
    };
    LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, filter);

		// toolbar options
		mToolbar.setOnMenuItemClickListener(menuItem ->
		{
			switch (menuItem.getItemId())
			{
				case R.id.menu_add_directory:
					launchFileListActivity();
					return true;

				case R.id.menu_settings_core:
					launchSettingsActivity(MenuTag.CONFIG);
					return true;

				case R.id.menu_settings_gcpad:
					launchSettingsActivity(MenuTag.GCPAD_TYPE);
					return true;

				case R.id.menu_settings_wiimote:
					launchSettingsActivity(MenuTag.WIIMOTE);
					return true;

				case R.id.menu_clear_data:
					clearGameData(this);
					return true;

				case R.id.menu_refresh:
					GameFileCacheService.startRescan(this);
					return true;

				case R.id.menu_open_file:
					launchOpenFileActivity();
					return true;

				case R.id.menu_install_wad:
					launchInstallWAD();
					return true;

				case R.id.updater_dialog:
					openUpdaterDialog();
					return true;
			}
			return false;
		});

    // Stuff in this block only happens when this activity is newly created (i.e. not a rotation)
    if (savedInstanceState == null)
      StartupHandler.HandleInit(this);

    if (PermissionsHandler.hasWriteAccess(this))
    {
			PlatformPagerAdapter platformPagerAdapter = new PlatformPagerAdapter(
				getSupportFragmentManager(), this);
			mViewPager.setAdapter(platformPagerAdapter);
      showGames();
      GameFileCacheService.startLoad(this);
    }
		else
		{
			mViewPager.setVisibility(View.INVISIBLE);
		}
		mViewPager.setOffscreenPageLimit(3);
  }

  @Override
  protected void onResume()
  {
    super.onResume();
    if (mDirToAdd != null)
    {
      GameFileCache.addGameFolder(mDirToAdd);
      mDirToAdd = null;
      GameFileCacheService.startRescan(this);
    }
  }

  @Override
  protected void onDestroy()
  {
    super.onDestroy();
    if (mBroadcastReceiver != null)
    {
      LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
    }
  }

  // TODO: Replace with a ButterKnife injection.
  private void findViews()
  {
    mToolbar = findViewById(R.id.toolbar_main);
		mViewPager = findViewById(R.id.pager_platforms);
		mTabLayout = findViewById(R.id.tabs_platforms);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.menu_game_grid, menu);
    return true;
  }

  public void launchSettingsActivity(MenuTag menuTag)
  {
    SettingsActivity.launch(this, menuTag, "");
  }

  public void launchFileListActivity()
  {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		startActivityForResult(intent, REQUEST_ADD_DIRECTORY);
  }

	public void openUpdaterDialog()
	{
		UpdaterUtils.openUpdaterWindow(this, null);
	}

  private void clearGameData(Context context)
  {
    int count = 0;
    String cachePath = DirectoryInitialization.getCacheDirectory();
    File dir = new File(cachePath);
    if (dir.exists())
    {
      for (File f : dir.listFiles())
      {
        if (f.getName().endsWith(".uidcache"))
        {
          if (f.delete())
          {
            count += 1;
          }
        }
      }
    }

    String shadersPath = cachePath + File.separator + "Shaders";
    dir = new File(shadersPath);
    if (dir.exists())
    {
      for (File f : dir.listFiles())
      {
        if (f.getName().endsWith(".cache"))
        {
          if (f.delete())
          {
            count += 1;
          }
        }
      }
    }

    Toast.makeText(context, context.getString(R.string.delete_cache_toast, count),
      Toast.LENGTH_SHORT).show();
  }

  public void launchOpenFileActivity()
  {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		startActivityForResult(intent, REQUEST_OPEN_FILE);
  }

	public void launchInstallWAD()
	{
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		startActivityForResult(intent, REQUEST_INSTALL_WAD);
	}

	public void installWAD(String file)
	{
		AlertDialog dialog = new AlertDialog.Builder(this).create();
		dialog.setTitle("Installing WAD");
		dialog.setMessage("Installing...");
		dialog.setCancelable(false);
		dialog.show();

		Thread installWADThread = new Thread(() ->
		{
			if (NativeLibrary.InstallWAD(file))
			{
				runOnUiThread(
					() -> Toast.makeText(this, R.string.wad_install_success, Toast.LENGTH_SHORT)
						.show());
			}
			else
			{
				runOnUiThread(
					() -> Toast.makeText(this, R.string.wad_install_failure, Toast.LENGTH_SHORT)
						.show());
			}
			runOnUiThread(dialog::dismiss);
		}, "InstallWAD");
		installWADThread.start();
	}

	public void onDirectorySelected(Intent result)
	{
		Uri uri = result.getData();

		String[] childNames = SafHandler.getChildNames(uri, false);
		if (Arrays.stream(childNames).noneMatch((name) -> FileBrowserHelper.GAME_EXTENSIONS.contains(
			FileBrowserHelper.getExtension(name, false))))
		{
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(getString(R.string.wrong_file_extension_in_directory,
				FileBrowserHelper.setToSortedDelimitedString(FileBrowserHelper.GAME_EXTENSIONS)));
			builder.setPositiveButton(android.R.string.ok, null);
			builder.show();
		}

		ContentResolver contentResolver = getContentResolver();
		Uri canonicalizedUri = contentResolver.canonicalize(uri);
		if (canonicalizedUri != null)
			uri = canonicalizedUri;

		int takeFlags = result.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
		getContentResolver().takePersistableUriPermission(uri, takeFlags);

		mDirToAdd = uri.toString();
	}

  /**
   * @param requestCode An int describing whether the Activity that is returning did so successfully.
   * @param resultCode  An int describing what Activity is giving us this callback.
   * @param result      The information the returning Activity is providing us.
   */
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent result)
  {
    switch (requestCode)
    {
      case REQUEST_ADD_DIRECTORY:
        // If the user picked a file, as opposed to just backing out.
        if (resultCode == RESULT_OK)
        {
          onDirectorySelected(result);
        }
        break;

      case REQUEST_OPEN_FILE:
        // If the user picked a file, as opposed to just backing out.
        if (resultCode == RESULT_OK)
        {
					Uri uri = result.getData();
					FileBrowserHelper.runAfterExtensionCheck(this, uri,
						FileBrowserHelper.GAME_LIKE_EXTENSIONS,
						() -> EmulationActivity.launch(this, result.getData().toString()));
        }
        break;


			case REQUEST_INSTALL_WAD:
				// If the user picked a file, as opposed to just backing out.
				if (resultCode == RESULT_OK)
				{
					Uri uri = result.getData();
					FileBrowserHelper.runAfterExtensionCheck(this, uri,
						FileBrowserHelper.WAD_EXTENSION,
						() -> installWAD(result.getData().toString()));
				}
				break;
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
  {
    switch (requestCode)
    {
      case PermissionsHandler.REQUEST_CODE_WRITE_PERMISSION:
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
        {
          DirectoryInitialization.start(this);
					PlatformPagerAdapter platformPagerAdapter = new PlatformPagerAdapter(
						getSupportFragmentManager(), this);
					mViewPager.setAdapter(platformPagerAdapter);
					mTabLayout.setupWithViewPager(mViewPager);
					mViewPager.setVisibility(View.VISIBLE);
          GameFileCacheService.startLoad(this);
        }
        else
        {
          Toast.makeText(this, R.string.write_permission_needed, Toast.LENGTH_SHORT)
            .show();
        }
        break;
      default:
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        break;
    }
  }

  public void showGames()
  {
		for (Platform platform : Platform.values())
		{
			PlatformGamesView fragment = getPlatformGamesView(platform);
			if (fragment != null)
			{
				fragment.showGames();
			}
		}
	}

	@Nullable
	private PlatformGamesView getPlatformGamesView(Platform platform)
	{
		String fragmentTag = "android:switcher:" + mViewPager.getId() + ":" + platform.toInt();

		return (PlatformGamesView) getSupportFragmentManager().findFragmentByTag(fragmentTag);
  }
}
