package org.dolphinemu.dolphinemu.model

import android.content.Context
import android.preference.PreferenceManager
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
        val folderPathsSet = preferences.getStringSet(GAME_FOLDER_PATHS_PREFERENCE, HashSet())
            ?: return false

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

        if (folderPathsSet.size != newFolderPaths.size) {
            // One or more folders are being deleted
            val editor = preferences.edit()
            editor.putStringSet(GAME_FOLDER_PATHS_PREFERENCE, newFolderPaths)
            editor.apply()
        }

        val folderPaths = folderPathsSet.toTypedArray()
        var cacheChanged = update(folderPaths)
        cacheChanged = cacheChanged or updateAdditionalMetadata()
        if (cacheChanged) {
            save()
        }
        return cacheChanged
    }

    @Synchronized
    external fun getAllGames(): Array<GameFile>

    @Synchronized
    external fun addOrGet(gamePath: String): GameFile?

    @Synchronized
    private external fun update(folderPaths: Array<String>): Boolean

    @Synchronized
    private external fun updateAdditionalMetadata(): Boolean

    @Synchronized
    external fun load(): Boolean

    @Synchronized
    private external fun save(): Boolean

    companion object {
        private const val GAME_FOLDER_PATHS_PREFERENCE = "gameFolderPaths"

        fun addGameFolder(path: String, context: Context?) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val folderPaths = preferences.getStringSet(GAME_FOLDER_PATHS_PREFERENCE, HashSet())
                ?: return

            val newFolderPaths: MutableSet<String> = HashSet(folderPaths)
            newFolderPaths.add(path)
            val editor = preferences.edit()
            editor.putStringSet(GAME_FOLDER_PATHS_PREFERENCE, newFolderPaths)
            editor.apply()
        }

        @JvmStatic
        private external fun init()
    }
}
