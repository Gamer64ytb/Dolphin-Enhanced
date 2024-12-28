package org.dolphinemu.dolphinemu.model

import android.content.Context
import android.preference.PreferenceManager
import org.dolphinemu.dolphinemu.services.GameFileCacheService
import org.dolphinemu.dolphinemu.utils.ContentHandler
import java.io.File

class GameFileCache {
    init {
        init()
    }

    /**
     * Scans through the file system and updates the cache to match.
     *
     * @return true if the cache was modified
     */
    fun scanLibrary(context: Context?): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val folderPathsSet =
            preferences.getStringSet(GAME_FOLDER_PATHS_PREFERENCE, HashSet())!!

        // get paths from gamefiles
        val gameFiles = GameFileCacheService.getAllGameFiles()
        for (f in gameFiles) {
            val filename = f.path
            val lastSep = filename!!.lastIndexOf(File.separator)
            if (lastSep > 0) {
                val path = filename.substring(0, lastSep)
                if (!folderPathsSet.contains(path)) {
                    folderPathsSet.add(path)
                }
            }
        }

        // remove non exists paths
        val newFolderPaths: MutableSet<String> = HashSet()
        for (folderPath in folderPathsSet) {
            if (if (folderPath.startsWith("content://")) ContentHandler.exists(folderPath) else File(
                    folderPath
                ).exists()
            ) {
                newFolderPaths.add(folderPath)
            }
        }

        // apply changes
        if (folderPathsSet.size != newFolderPaths.size) {
            // One or more folders are being deleted
            val editor = preferences.edit()
            editor.putStringSet(GAME_FOLDER_PATHS_PREFERENCE, newFolderPaths)
            editor.apply()
        }

        val folderPaths = folderPathsSet.toTypedArray<String>()
        var cacheChanged = update(folderPaths)
        cacheChanged = cacheChanged or updateAdditionalMetadata()
        if (cacheChanged) {
            save()
        }
        return cacheChanged
    }

    val allGames: Array<GameFile?>?
        external get

    external fun addOrGet(gamePath: String?): GameFile?

    private external fun update(folderPaths: Array<String>): Boolean

    private external fun updateAdditionalMetadata(): Boolean

    external fun load(): Boolean

    private external fun save(): Boolean

    companion object {
        private const val GAME_FOLDER_PATHS_PREFERENCE = "gameFolderPaths"

        fun addGameFolder(path: String, context: Context?) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val folderPaths =
                preferences.getStringSet(GAME_FOLDER_PATHS_PREFERENCE, HashSet())!!
            if (!folderPaths.contains(path)) {
                folderPaths.add(path)
                val editor = preferences.edit()
                editor.putStringSet(GAME_FOLDER_PATHS_PREFERENCE, folderPaths)
                editor.apply()
            }
        }

        @JvmStatic
        private external fun init()
    }
}
