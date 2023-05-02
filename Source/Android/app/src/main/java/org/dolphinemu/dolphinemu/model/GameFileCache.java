package org.dolphinemu.dolphinemu.model;

import org.dolphinemu.dolphinemu.NativeLibrary;
import org.dolphinemu.dolphinemu.features.settings.model.Settings;
import org.dolphinemu.dolphinemu.features.settings.utils.SettingsFile;
import org.dolphinemu.dolphinemu.utils.IniFile;
import org.dolphinemu.dolphinemu.utils.SafHandler;

import java.io.File;
import java.util.LinkedHashSet;

public class GameFileCache
{
  public GameFileCache()
  {
    init();
  }

  public static void addGameFolder(String path)
  {
		File dolphinFile = SettingsFile.getSettingsFile(SettingsFile.FILE_NAME_DOLPHIN);
		IniFile dolphinIni = new IniFile(dolphinFile);
		LinkedHashSet<String> pathSet = getPathSet(false);
		int totalISOPaths =
			dolphinIni.getInt(Settings.SECTION_INI_INTERFACE, SettingsFile.KEY_ISO_PATHS, 0);
		if (!pathSet.contains(path))
		{
			dolphinIni.setInt(Settings.SECTION_INI_INTERFACE, SettingsFile.KEY_ISO_PATHS,
				totalISOPaths + 1);
			dolphinIni.setString(Settings.SECTION_INI_INTERFACE, SettingsFile.KEY_ISO_PATH_BASE +
				totalISOPaths, path);
			dolphinIni.save(dolphinFile);
			NativeLibrary.ReloadConfig();
		}
  }

	private static LinkedHashSet<String> getPathSet(boolean removeNonExistentFolders)
	{
		File dolphinFile = SettingsFile.getSettingsFile(SettingsFile.FILE_NAME_DOLPHIN);
		IniFile dolphinIni = new IniFile(dolphinFile);
		LinkedHashSet<String> pathSet = new LinkedHashSet<>();
		int totalISOPaths =
			dolphinIni.getInt(Settings.SECTION_INI_INTERFACE, SettingsFile.KEY_ISO_PATHS, 0);

		for (int i = 0; i < totalISOPaths; i++)
		{
			String path = dolphinIni.getString(Settings.SECTION_INI_INTERFACE,
				SettingsFile.KEY_ISO_PATH_BASE + i, "");

			if (path.startsWith("content://") ? SafHandler.exists(path) : new File(path).exists())
			{
				pathSet.add(path);
			}
		}

		if (removeNonExistentFolders && totalISOPaths > pathSet.size())
		{
			int setIndex = 0;

			dolphinIni.setInt(Settings.SECTION_INI_INTERFACE, SettingsFile.KEY_ISO_PATHS,
				pathSet.size());

			// One or more folders have been removed.
			for (String entry : pathSet)
			{
				dolphinIni.setString(Settings.SECTION_INI_INTERFACE, SettingsFile.KEY_ISO_PATH_BASE +
					setIndex, entry);

				setIndex++;
			}

			// Delete known unnecessary keys. Ignore i values beyond totalISOPaths.
			for (int i = setIndex; i < totalISOPaths; i++)
			{
				dolphinIni.deleteKey(Settings.SECTION_INI_INTERFACE, SettingsFile.KEY_ISO_PATH_BASE + i);
			}

			dolphinIni.save(dolphinFile);
			NativeLibrary.ReloadConfig();
		}

		return pathSet;
	}

  /**
   * Scans through the file system and updates the cache to match.
   *
   * @return true if the cache was modified
   */
  public boolean scanLibrary()
  {
		LinkedHashSet<String> folderPathsSet = getPathSet(true);

		String[] folderPaths = folderPathsSet.toArray(new String[0]);

		boolean cacheChanged = update(folderPaths);
		cacheChanged |= updateAdditionalMetadata();
		if (cacheChanged)
		{
			save();
		}
		return cacheChanged;
  }

  public native GameFile[] getAllGames();

  public native GameFile addOrGet(String gamePath);

  private native boolean update(String[] folderPaths);

  private native boolean updateAdditionalMetadata();

  public native boolean load();

  private native boolean save();

  private static native void init();
}
