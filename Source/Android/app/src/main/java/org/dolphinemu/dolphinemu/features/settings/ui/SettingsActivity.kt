package org.dolphinemu.dolphinemu.features.settings.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.Setting
import org.dolphinemu.dolphinemu.features.settings.model.Settings
import org.dolphinemu.dolphinemu.utils.ThemeUtil

class SettingsActivity : AppCompatActivity(), SettingsActivityView {
    private var mSettings: Settings? = Settings()
    private var mStackCount = 0
    private var mShouldSave = false
    private var mMenuTag: MenuTag? = null
    var gameId: String? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        val launcher = intent
        val gameId = launcher.getStringExtra(ARG_GAME_ID)
        val menuTag = launcher.getSerializableExtra(ARG_MENU_TAG) as MenuTag?

        if (savedInstanceState == null) {
            mMenuTag = menuTag
            this.gameId = gameId
        } else {
            val menuTagStr = savedInstanceState.getString(KEY_MENU_TAG)
            mShouldSave = savedInstanceState.getBoolean(KEY_SHOULD_SAVE)
            mMenuTag = MenuTag.getMenuTag(menuTagStr)
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

        outState.putBoolean(KEY_SHOULD_SAVE, mShouldSave)
        outState.putString(KEY_MENU_TAG, mMenuTag.toString())
        outState.putString(KEY_GAME_ID, gameId)
    }

    override fun onStart() {
        super.onStart()
        loadSettingsUI()
    }

    private fun loadSettingsUI() {
        if (mSettings!!.isEmpty) {
            mSettings!!.loadSettings(gameId)
        }

        showSettingsFragment(mMenuTag!!, null, false, gameId!!)
    }

    /**
     * If this is called, the user has left the settings screen (potentially through the
     * home button) and will expect their changes to be persisted. So we kick off an
     * IntentService which will do so on a background thread.
     */
    override fun onStop() {
        super.onStop()

        if (mSettings != null && isFinishing && mShouldSave) {
            if (TextUtils.isEmpty(gameId)) {
                showToastMessage(getString(R.string.settings_saved_notice))
                mSettings!!.saveSettings()
                ThemeUtil.applyTheme(mSettings)
            } else {
                // custom game settings
                showToastMessage(getString(R.string.settings_saved_notice))
                mSettings!!.saveCustomGameSettings(gameId)
            }
            mShouldSave = false
        }

        fragment!!.closeDialog()
        mStackCount = 0
    }

    override fun onBackPressed() {
        if (mStackCount > 0) {
            supportFragmentManager.popBackStackImmediate()
            mStackCount--
        } else {
            finish()
        }
    }

    override fun showSettingsFragment(
        menuTag: MenuTag,
        extras: Bundle?,
        addToStack: Boolean,
        gameID: String
    ) {
        val transaction = supportFragmentManager.beginTransaction()

        if (addToStack) {
            if (areSystemAnimationsEnabled()) {
                transaction.setCustomAnimations(
                    R.animator.settings_enter,
                    R.animator.settings_exit,
                    R.animator.settings_pop_enter,
                    R.animator.setttings_pop_exit
                )
            }

            transaction.addToBackStack(null)
            mStackCount++
        }
        transaction.replace(
            R.id.frame_content,
            SettingsFragment.newInstance(menuTag, gameID, extras),
            FRAGMENT_TAG
        )
        transaction.commit()

        // show settings
        val fragment: SettingsFragmentView? = fragment
        fragment?.showSettingsList(mSettings)
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

    fun getSettings(): Settings? {
        return mSettings
    }

    override fun setSettings(settings: Settings) {
        mSettings = settings
    }

    fun putSetting(setting: Setting) {
        mSettings!!.getSection(setting.section).putSetting(setting)
    }

    fun setSettingChanged() {
        mShouldSave = true
    }

    fun loadSubMenu(menuKey: MenuTag) {
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

    companion object {
        const val ARG_CONTROLLER_TYPE: String = "controller_type"

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
