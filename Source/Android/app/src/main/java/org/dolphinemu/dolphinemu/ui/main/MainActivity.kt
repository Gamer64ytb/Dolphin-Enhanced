package org.dolphinemu.dolphinemu.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.activities.EmulationActivity.Companion.launch
import org.dolphinemu.dolphinemu.adapters.GameAdapter
import org.dolphinemu.dolphinemu.features.settings.ui.MenuTag
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsActivity
import org.dolphinemu.dolphinemu.model.GameFileCache
import org.dolphinemu.dolphinemu.services.GameFileCacheService
import org.dolphinemu.dolphinemu.utils.ContentHandler
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization
import org.dolphinemu.dolphinemu.utils.FileBrowserHelper
import org.dolphinemu.dolphinemu.utils.PermissionsHandler
import org.dolphinemu.dolphinemu.utils.StartupHandler
import org.dolphinemu.dolphinemu.utils.ThemeUtil
import java.io.File
import java.util.Arrays

/**
 * The main Activity of the Lollipop style UI. Manages several PlatformGamesFragments, which
 * individually display a grid of available games for each Fragment, in a tabbed layout.
 */
class MainActivity : AppCompatActivity() {
    private var mDivider: DividerItemDecoration? = null
    private var mGameList: RecyclerView? = null
    private var mAdapter: GameAdapter? = null
    private var mToolbar: Toolbar? = null
    private var mBroadcastReceiver: BroadcastReceiver? = null
    private var mDirToAdd: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.applyTheme()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViews()
        setSupportActionBar(mToolbar)
        title = String(TITLE_BYTES)

        val filter = IntentFilter()
        filter.addAction(GameFileCacheService.BROADCAST_ACTION)
        mBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                showGames()
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver!!, filter)

        // Stuff in this block only happens when this activity is newly created (i.e. not a rotation)
        if (savedInstanceState == null) StartupHandler.HandleInit(this)

        if (PermissionsHandler.hasWriteAccess(this)) {
            showGames()
            GameFileCacheService.startLoad(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (mDirToAdd != null) {
            GameFileCache.addGameFolder(mDirToAdd, this)
            mDirToAdd = null
            GameFileCacheService.startRescan(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
            mBroadcastReceiver!!
        )
    }

    // TODO: Replace with a ButterKnife injection.
    private fun findViews() {
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        mDivider = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        mToolbar = findViewById(R.id.toolbar_main)
        mGameList = findViewById(R.id.grid_games)
        mAdapter = GameAdapter()
        mGameList!!.setAdapter(mAdapter)
        refreshGameList(pref.getBoolean(PREF_GAMELIST, true))
    }

    private fun refreshGameList(flag: Boolean) {
        val resourceId: Int
        var columns = resources.getInteger(R.integer.game_grid_columns)
        val layoutManager: RecyclerView.LayoutManager
        if (flag) {
            resourceId = R.layout.card_game
            layoutManager = GridLayoutManager(this, columns)
            mGameList!!.addItemDecoration(mDivider!!)
        } else {
            columns = columns * 2 + 1
            resourceId = R.layout.card_game2
            layoutManager = GridLayoutManager(this, columns)
            mGameList!!.removeItemDecoration(mDivider!!)
        }
        mAdapter!!.setResourceId(resourceId)
        mGameList!!.layoutManager = layoutManager
    }

    private fun toggleGameList() {
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        val flag = !pref.getBoolean(PREF_GAMELIST, true)
        pref.edit().putBoolean(PREF_GAMELIST, flag).apply()
        refreshGameList(flag)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_game_grid, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add_directory -> {
                launchFileListActivity()
                return true
            }

            R.id.menu_toggle_list -> {
                toggleGameList()
                return true
            }

            R.id.menu_settings_core -> {
                launchSettingsActivity(MenuTag.CONFIG)
                return true
            }

            R.id.menu_settings_gcpad -> {
                launchSettingsActivity(MenuTag.GCPAD_TYPE)
                return true
            }

            R.id.menu_settings_wiimote -> {
                launchSettingsActivity(MenuTag.WIIMOTE)
                return true
            }

            R.id.menu_clear_data -> {
                clearGameData(this)
                return true
            }

            R.id.menu_refresh -> {
                GameFileCacheService.startRescan(this)
                return true
            }

            R.id.menu_open_file -> {
                launchOpenFileActivity()
                return true
            }
        }

        return false
    }

    private fun launchSettingsActivity(menuTag: MenuTag?) {
        SettingsActivity.launch(this, menuTag, "")
    }

    private fun launchFileListActivity() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_ADD_DIRECTORY)
    }

    private fun clearGameData(context: Context) {
        var count = 0
        val cachePath = DirectoryInitialization.getCacheDirectory(context)
        var dir = File(cachePath)
        if (dir.exists()) {
            for (f in dir.listFiles()) {
                if (f.name.endsWith(".uidcache")) {
                    if (f.delete()) {
                        count += 1
                    }
                }
            }
        }

        val shadersPath = cachePath + File.separator + "Shaders"
        dir = File(shadersPath)
        if (dir.exists()) {
            for (f in dir.listFiles()) {
                if (f.name.endsWith(".cache")) {
                    if (f.delete()) {
                        count += 1
                    }
                }
            }
        }

        Toast.makeText(
            context, context.getString(R.string.delete_cache_toast, count),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun launchOpenFileActivity() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("*/*")
        startActivityForResult(intent, REQUEST_OPEN_FILE)
    }

    private fun onDirectorySelected(result: Intent) {
        var uri = result.data

        val childNames = ContentHandler.getChildNames(
            uri!!, false
        )
        if (Arrays.stream(childNames).noneMatch { name: String? ->
                FileBrowserHelper.GAME_EXTENSIONS.contains(
                    FileBrowserHelper.getExtension(name, false)
                )
            }) {
            val builder = AlertDialog.Builder(this)
            builder.setMessage(
                getString(
                    R.string.wrong_file_extension_in_directory,
                    FileBrowserHelper.setToSortedDelimitedString(FileBrowserHelper.GAME_EXTENSIONS)
                )
            )
            builder.setPositiveButton(android.R.string.ok, null)
            builder.show()
        }

        val contentResolver = contentResolver
        val canonicalizedUri = contentResolver.canonicalize(uri)
        if (canonicalizedUri != null) uri = canonicalizedUri

        val takeFlags = result.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        getContentResolver().takePersistableUriPermission(uri, takeFlags)

        mDirToAdd = uri.toString()
    }

    /**
     * @param requestCode An int describing whether the Activity that is returning did so successfully.
     * @param resultCode  An int describing what Activity is giving us this callback.
     * @param result      The information the returning Activity is providing us.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, result: Intent?) {
        super.onActivityResult(requestCode, resultCode, result)

        // If the user picked a file, as opposed to just backing out.
        if (resultCode == RESULT_OK) {
            val uri = result!!.data
            when (requestCode) {
                REQUEST_ADD_DIRECTORY ->           // If the user picked a file, as opposed to just backing out.
                    onDirectorySelected(result)

                REQUEST_OPEN_FILE ->           // If the user picked a file, as opposed to just backing out.
                    FileBrowserHelper.runAfterExtensionCheck(
                        this, uri,
                        FileBrowserHelper.GAME_LIKE_EXTENSIONS
                    ) { launch(this, result.data.toString()) }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PermissionsHandler.REQUEST_CODE_WRITE_PERMISSION -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                DirectoryInitialization.start(this)
                GameFileCacheService.startLoad(this)
            } else {
                Toast.makeText(this, R.string.write_permission_needed, Toast.LENGTH_SHORT)
                    .show()
            }

            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    fun showGames() {
        mAdapter!!.swapDataSet(GameFileCacheService.getAllGameFiles())
    }

    companion object {
        const val REQUEST_ADD_DIRECTORY: Int = 1
        const val REQUEST_OPEN_FILE: Int = 2
        private const val PREF_GAMELIST = "GAME_LIST_TYPE"
        private val TITLE_BYTES = byteArrayOf(
            0x44,
            0x6f,
            0x6c,
            0x70,
            0x68,
            0x69,
            0x6e,
            0x20,
            0x35,
            0x2e,
            0x30,
            0x28,
            0x4d,
            0x4d,
            0x4a,
            0x29
        )
    }
}
