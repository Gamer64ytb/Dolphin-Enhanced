package org.dolphinemu.dolphinemu.features.settings.ui

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.Setting
import org.dolphinemu.dolphinemu.features.settings.model.Settings
import org.dolphinemu.dolphinemu.features.settings.utils.SettingsFile
import org.dolphinemu.dolphinemu.model.GpuDriverMetadata
import org.dolphinemu.dolphinemu.ui.main.MainActivity
import org.dolphinemu.dolphinemu.utils.GpuDriverHelper
import org.dolphinemu.dolphinemu.utils.GpuDriverInstallResult
import org.dolphinemu.dolphinemu.utils.ThemeUtil

class SettingsActivity : AppCompatActivity(), SettingsActivityView {
    private var settings: Settings = Settings()
    private var stackCount = 0
    private var shouldSave = false
    private var menuTag: MenuTag? = null
    var gameId: String? = null
        private set

    var gpuDriver: GpuDriverMetadata? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        val launcher = intent
        val gameId = launcher.getStringExtra(ARG_GAME_ID)
        val menuTag = launcher.getSerializableExtra(ARG_MENU_TAG) as MenuTag?

        if (savedInstanceState == null) {
            this.menuTag = menuTag
            this.gameId = gameId
        } else {
            val menuTagStr = savedInstanceState.getString(KEY_MENU_TAG)
            shouldSave = savedInstanceState.getBoolean(KEY_SHOULD_SAVE)
            this.menuTag = MenuTag.getMenuTag(menuTagStr)
            this.gameId = savedInstanceState.getString(KEY_GAME_ID)
        }

        if (!TextUtils.isEmpty(this.gameId)) {
            title = getString(R.string.per_game_settings, this.gameId)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_save_exit) {
            finish()
            return true
        }
        return false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Critical: If super method is not called, rotations will be busted.
        super.onSaveInstanceState(outState)

        outState.putBoolean(KEY_SHOULD_SAVE, shouldSave)
        outState.putString(KEY_MENU_TAG, menuTag.toString())
        outState.putString(KEY_GAME_ID, gameId)
    }

    override fun onStart() {
        super.onStart()
        loadSettingsUI()
    }

    private fun loadSettingsUI() {
        if (settings.isEmpty) {
            settings.loadSettings(gameId)
        }

        showSettingsFragment(menuTag!!, null, false, gameId!!)
    }

    /**
     * If this is called, the user has left the settings screen (potentially through the
     * home button) and will expect their changes to be persisted. So we kick off an
     * IntentService which will do so on a background thread.
     */
    override fun onStop() {
        super.onStop()

        if (isFinishing && shouldSave) {
            if (TextUtils.isEmpty(gameId)) {
                showToastMessage(getString(R.string.settings_saved_notice))
                settings.saveSettings()
                ThemeUtil.applyTheme(settings)
            } else {
                // custom game settings
                showToastMessage(getString(R.string.settings_saved_notice))
                settings.saveCustomGameSettings(gameId)
            }
            shouldSave = false
        }

        fragment!!.closeDialog()
        stackCount = 0
    }

    override fun onBackPressed() {
        if (stackCount > 0) {
            supportFragmentManager.popBackStackImmediate()
            stackCount--
        } else {
            finish()
        }
    }

    override fun showSettingsFragment(
        menuTag: MenuTag,
        extras: Bundle?,
        addToStack: Boolean,
        gameId: String
    ) {
        val transaction = supportFragmentManager.beginTransaction()

        if (addToStack) {
            if (areSystemAnimationsEnabled()) {
                transaction.setCustomAnimations(
                    R.anim.anim_settings_fragment_in,
                    R.anim.anim_settings_fragment_out,
                    0,
                    R.anim.anim_pop_settings_fragment_out
                )
            }

            transaction.addToBackStack(null)
            stackCount++
        }
        transaction.replace(
            R.id.frame_content,
            SettingsFragment.newInstance(menuTag, gameId, extras),
            FRAGMENT_TAG
        )
        transaction.commit()

        // show settings
        val fragment: SettingsFragmentView? = fragment
        fragment?.showSettingsList(settings)
    }

    private fun areSystemAnimationsEnabled(): Boolean {
        val duration = android.provider.Settings.Global.getFloat(
            contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        )

        val transition = android.provider.Settings.Global.getFloat(
            contentResolver,
            android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE, 1f
        )

        return duration != 0f && transition != 0f
    }

    override fun showPermissionNeededHint() {
        Toast.makeText(this, R.string.write_permission_needed, Toast.LENGTH_SHORT)
            .show()
    }

    override fun showExternalStorageNotMountedHint() {
        Toast.makeText(this, R.string.external_storage_not_mounted, Toast.LENGTH_SHORT)
            .show()
    }

    fun getSettings(): Settings {
        return settings
    }

    override fun setSettings(settings: Settings) {
        this.settings = settings
    }

    fun putSetting(setting: Setting) {
        settings.getSection(setting.section).putSetting(setting)
    }

    fun setSettingChanged() {
        shouldSave = true
    }

    fun loadSubMenu(menuKey: MenuTag) {
        if (menuKey == MenuTag.GPU_DRIVERS) {
            showGpuDriverDialog()
            return
        }

        showSettingsFragment(menuKey, null, true, gameId!!)
    }

    override fun showToastMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onGcPadSettingChanged(menuTag: MenuTag, value: Int) {
        if (value != 0)  // Not disabled
        {
            val bundle = Bundle()
            bundle.putInt(ARG_CONTROLLER_TYPE, value / 6)
            showSettingsFragment(menuTag, bundle, true, gameId!!)
        }
    }

    override fun onWiimoteSettingChanged(menuTag: MenuTag, value: Int) {
        when (value) {
            1 -> showSettingsFragment(menuTag, null, true, gameId!!)
            2 -> showToastMessage("Please make sure Continuous Scanning is enabled in Core Settings.")
        }
    }

    override fun onExtensionSettingChanged(menuTag: MenuTag, value: Int) {
        if (value != 0)  // None
        {
            val bundle = Bundle()
            bundle.putInt(ARG_CONTROLLER_TYPE, value)
            showSettingsFragment(menuTag, bundle, true, gameId!!)
        }
    }

    private val fragment: SettingsFragment?
        get() = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as SettingsFragment?

    private fun showGpuDriverDialog() {
        if (gpuDriver == null) {
            return
        }
        val msg = "${gpuDriver!!.name} ${gpuDriver!!.driverVersion}"

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.gpu_driver_dialog_title))
            .setMessage(msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.gpu_driver_dialog_system) { _: DialogInterface?, _: Int ->
                useSystemDriver()
            }
            .setPositiveButton(R.string.gpu_driver_dialog_install) { _: DialogInterface?, _: Int ->
                askForDriverFile()
            }
            .show()
    }

    private fun askForDriverFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            type = "application/zip"
        }
        startActivityForResult(intent, MainActivity.REQUEST_GPU_DRIVER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // If the user picked a file, as opposed to just backing out.
        if (resultCode != RESULT_OK) {
            return
        }

        if (requestCode != MainActivity.REQUEST_GPU_DRIVER) {
            return
        }

        val uri = data?.data ?: return
        installDriver(uri)
    }

    private fun onDriverInstallDone(result: GpuDriverInstallResult) {
        Snackbar
            .make(fragment!!.requireView(), resolveInstallResultString(result), Snackbar.LENGTH_LONG)
            .show()
    }

    private fun onDriverUninstallDone() {
        Toast.makeText(
            this,
            R.string.gpu_driver_dialog_uninstall_done,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun installDriver(uri: Uri) {
        val context = this
        CoroutineScope(Dispatchers.IO).launch {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream == null) {
                GpuDriverHelper.uninstallDriver()
                withContext(Dispatchers.Main) {
                    onDriverInstallDone(GpuDriverInstallResult.FileNotFound)
                }
                return@launch
            }

            val result = GpuDriverHelper.installDriver(stream)
            withContext(Dispatchers.Main) {
                with(this) {
                    gpuDriver = GpuDriverHelper.getInstalledDriverMetadata()
                        ?: GpuDriverHelper.getSystemDriverMetadata(context) ?: return@withContext
                    NativeLibrary.SetConfig(SettingsFile.FILE_NAME_GFX + ".ini",
                        SettingsFile.KEY_GPU_DRIVERS, Settings.SECTION_GFX_SETTINGS, gpuDriver!!.libraryName)
                }
                onDriverInstallDone(result)
            }
        }
    }

    private fun useSystemDriver() {
        CoroutineScope(Dispatchers.IO).launch {
            GpuDriverHelper.uninstallDriver()
            withContext(Dispatchers.Main) {
                with(this) {
                    gpuDriver =
                        GpuDriverHelper.getInstalledDriverMetadata()
                            ?: GpuDriverHelper.getSystemDriverMetadata(applicationContext)
                    NativeLibrary.SetConfig(SettingsFile.FILE_NAME_GFX + ".ini",
                        SettingsFile.KEY_GPU_DRIVERS, Settings.SECTION_GFX_SETTINGS, "")
                }
                onDriverUninstallDone()
            }
        }
    }

    private fun resolveInstallResultString(result: GpuDriverInstallResult) = when (result) {
        GpuDriverInstallResult.Success -> getString(R.string.gpu_driver_install_success)
        GpuDriverInstallResult.InvalidArchive -> getString(R.string.gpu_driver_install_invalid_archive)
        GpuDriverInstallResult.MissingMetadata -> getString(R.string.gpu_driver_install_missing_metadata)
        GpuDriverInstallResult.InvalidMetadata -> getString(R.string.gpu_driver_install_invalid_metadata)
        GpuDriverInstallResult.UnsupportedAndroidVersion -> getString(R.string.gpu_driver_install_unsupported_android_version)
        GpuDriverInstallResult.AlreadyInstalled -> getString(R.string.gpu_driver_install_already_installed)
        GpuDriverInstallResult.FileNotFound -> getString(R.string.gpu_driver_install_file_not_found)
    }

    companion object {
        const val ARG_CONTROLLER_TYPE = "controller_type"

        private const val ARG_MENU_TAG = "menu_tag"
        private const val ARG_GAME_ID = "game_id"
        private const val FRAGMENT_TAG = "settings"

        private const val KEY_SHOULD_SAVE = "should_save"
        private const val KEY_MENU_TAG = "menu_tag"
        private const val KEY_GAME_ID = "game_id"

        @JvmStatic
        fun launch(context: Context, menuTag: MenuTag?, gameId: String?) {
            val settings = Intent(context, SettingsActivity::class.java)
            settings.putExtra(ARG_MENU_TAG, menuTag)
            settings.putExtra(ARG_GAME_ID, gameId)
            context.startActivity(settings)
        }
    }
}
