package org.dolphinemu.dolphinemu.activities

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.DisplayMetrics
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.dialogs.RunningSettingDialog
import org.dolphinemu.dolphinemu.dialogs.StateSavesDialog
import org.dolphinemu.dolphinemu.features.settings.model.BooleanSetting
import org.dolphinemu.dolphinemu.features.settings.model.Settings
import org.dolphinemu.dolphinemu.features.settings.utils.SettingsFile
import org.dolphinemu.dolphinemu.fragments.EmulationFragment
import org.dolphinemu.dolphinemu.model.GameFile
import org.dolphinemu.dolphinemu.overlay.InputOverlay
import org.dolphinemu.dolphinemu.services.GameFileCacheService
import org.dolphinemu.dolphinemu.ui.platform.Platform
import org.dolphinemu.dolphinemu.utils.ControllerMappingHelper
import org.dolphinemu.dolphinemu.utils.Java_GCAdapter
import org.dolphinemu.dolphinemu.utils.Java_WiimoteAdapter
import org.dolphinemu.dolphinemu.utils.Rumble
import java.io.File
import java.lang.ref.WeakReference
import kotlin.math.abs

open class EmulationActivity : AppCompatActivity() {
    private var sensorManager: SensorManager? = null
    private var emulationFragment: EmulationFragment? = null

    private var preferences: SharedPreferences? = null
    private var controllerMappingHelper: ControllerMappingHelper? = null

    private var menuVisible = false
    private var bindingDevice: String? = null
    private var bindingButton = 0

    private var settings: Settings? = null

    private var selectedTitle: String? = ""
    var selectedGameId: String? = ""
        private set
    private var platform = 0
    private var paths: Array<String>? = null
    var savedState: String? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sActivity = WeakReference(this)

        if (savedInstanceState == null) {
            val gameToEmulate = intent
            paths = gameToEmulate.getStringArrayExtra(EXTRA_SELECTED_GAMES)
            savedState = gameToEmulate.getStringExtra(EXTRA_SAVED_STATE)
            if (paths != null && paths!!.isNotEmpty()) {
                val game = GameFileCacheService.getGameFileByPath(paths!![0])
                if (game != null) {
                    selectedGameId = game.getGameId()
                    selectedTitle = game.title
                    platform = game.getPlatform()
                    if (paths!!.size == 1) {
                        paths = GameFileCacheService.getAllDiscPaths(game)
                    }
                }
            }
        } else {
            restoreState(savedInstanceState)
        }

        settings = Settings()
        settings!!.loadSettings(null)

        controllerMappingHelper = ControllerMappingHelper()

        // Set these options now so that the SurfaceView the game renders into is the right size.
        enableFullscreenImmersive()

        Java_GCAdapter.manager = getSystemService(USB_SERVICE) as UsbManager
        Java_WiimoteAdapter.manager = getSystemService(USB_SERVICE) as UsbManager
        Rumble.initDeviceRumble()

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        NativeLibrary.SetScaledDensity(metrics.scaledDensity)

        title = selectedTitle
        setContentView(R.layout.activity_emulation)

        // Find or create the EmulationFragment
        emulationFragment = supportFragmentManager
            .findFragmentById(R.id.frame_emulation_fragment) as EmulationFragment?
        if (emulationFragment == null) {
            emulationFragment = EmulationFragment.newInstance(paths)
            supportFragmentManager.beginTransaction()
                .add(R.id.frame_emulation_fragment, emulationFragment!!)
                .commit()
        }

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        loadPreferences()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            enableFullscreenImmersive()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sActivity.clear()
        savePreferences()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (!isChangingConfigurations) {
            savedState = filesDir.toString() + File.separator + "temp.sav"
            NativeLibrary.SaveStateAs(savedState, true)
        }
        outState.putStringArray(EXTRA_SELECTED_GAMES, paths)
        outState.putString(EXTRA_SELECTED_TITLE, selectedTitle)
        outState.putString(EXTRA_SELECTED_GAMEID, selectedGameId)
        outState.putInt(EXTRA_PLATFORM, platform)
        outState.putString(EXTRA_SAVED_STATE, savedState)
        super.onSaveInstanceState(outState)
    }

    protected fun restoreState(savedInstanceState: Bundle) {
        paths = savedInstanceState.getStringArray(EXTRA_SELECTED_GAMES)
        selectedTitle = savedInstanceState.getString(EXTRA_SELECTED_TITLE)
        selectedGameId = savedInstanceState.getString(EXTRA_SELECTED_GAMEID)
        platform = savedInstanceState.getInt(EXTRA_PLATFORM)
        savedState = savedInstanceState.getString(EXTRA_SAVED_STATE)
    }

    override fun onBackPressed() {
        if (menuVisible) {
            emulationFragment!!.stopEmulation()
            finish()
        } else {
            menuVisible = true
            emulationFragment!!.stopConfiguringControls()
            val dialog = RunningSettingDialog.newInstance()
            dialog.show(supportFragmentManager, "RunningSettingDialog")
            dialog.setOnDismissListener {
                menuVisible = false
                enableFullscreenImmersive()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, result: Intent?) {
        super.onActivityResult(requestCode, resultCode, result)
        if (requestCode == REQUEST_CHANGE_DISC) {
            // If the user picked a file, as opposed to just backing out.
            if (resultCode == RESULT_OK) {
                NativeLibrary.ChangeDisc(result!!.data.toString())
            }
        }
    }

    private fun enableFullscreenImmersive() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    fun showStateSaves() {
        StateSavesDialog.newInstance(selectedGameId)
            .show(supportFragmentManager, "StateSavesDialog")
    }

    fun showJoystickSettings() {
        val joystick = InputOverlay.sJoyStickSetting
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.emulation_joystick_settings)

        builder.setSingleChoiceItems(
            R.array.wiiJoystickSettings, joystick
        ) { _: DialogInterface?, indexSelected: Int ->
            InputOverlay.sJoyStickSetting = indexSelected
        }
        builder.setOnDismissListener {
            if (InputOverlay.sJoyStickSetting != joystick) {
                emulationFragment!!.refreshInputOverlay()
            }
        }

        val alertDialog = builder.create()
        alertDialog.show()
    }

    fun showSensorSettings() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.emulation_sensor_settings)

        if (isGameCubeGame) {
            val sensor = InputOverlay.sSensorGCSetting
            builder.setSingleChoiceItems(
                R.array.gcSensorSettings, sensor
            ) { _: DialogInterface?, indexSelected: Int ->
                InputOverlay.sSensorGCSetting = indexSelected
            }
            builder.setOnDismissListener {
                setSensorState(
                    InputOverlay.sSensorGCSetting > 0
                )
            }
        } else {
            val sensor = InputOverlay.sSensorWiiSetting
            builder.setSingleChoiceItems(
                R.array.wiiSensorSettings, sensor
            ) { _: DialogInterface?, indexSelected: Int ->
                InputOverlay.sSensorWiiSetting = indexSelected
            }
            builder.setOnDismissListener {
                setSensorState(
                    InputOverlay.sSensorWiiSetting > 0
                )
            }
        }

        val alertDialog = builder.create()
        alertDialog.show()
    }

    private fun setSensorState(enabled: Boolean) {
        if (enabled) {
            if (sensorManager == null) {
                sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
                val rotationVector =
                    sensorManager!!.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                if (rotationVector != null) {
                    sensorManager!!.registerListener(
                        emulationFragment,
                        rotationVector,
                        SensorManager.SENSOR_DELAY_NORMAL,
                        SensorManager.SENSOR_DELAY_UI
                    )
                }
            }
        } else {
            if (sensorManager != null) {
                sensorManager!!.unregisterListener(emulationFragment)
                sensorManager = null
            }
        }

        emulationFragment!!.onAccuracyChanged(null, 0)
    }

    fun editControlsPlacement() {
        if (emulationFragment!!.isConfiguringControls) {
            emulationFragment!!.stopConfiguringControls()
        } else {
            emulationFragment!!.startConfiguringControls()
        }
    }

    override fun onResume() {
        super.onResume()

        val expandToCutoutAreaSetting: BooleanSetting? =
            settings?.getSection(Settings.SECTION_INI_INTERFACE)
                ?.getSetting(SettingsFile.KEY_EXPAND_TO_CUTOUT_AREA) as? BooleanSetting

        val expandToCutoutArea = expandToCutoutAreaSetting?.value ?: false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attributes = window.attributes

            attributes.layoutInDisplayCutoutMode = if (expandToCutoutArea) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }

            window.attributes = attributes
        }

        if (sensorManager != null) {
            val rotationVector = sensorManager!!.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            if (rotationVector != null) {
                sensorManager!!.registerListener(
                    emulationFragment,
                    rotationVector,
                    SensorManager.SENSOR_DELAY_NORMAL,
                    SensorManager.SENSOR_DELAY_UI
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()

        if (sensorManager != null) {
            sensorManager!!.unregisterListener(emulationFragment)
        }
    }

    private fun loadPreferences() {
        val id =
            if (selectedGameId!!.length > 3) selectedGameId!!.substring(0, 3) else selectedGameId!!
        val scaleKey = InputOverlay.CONTROL_SCALE_PREF_KEY + "_" + id
        val alphaKey = InputOverlay.CONTROL_ALPHA_PREF_KEY + "_" + id
        val typeKey = InputOverlay.CONTROL_TYPE_PREF_KEY + "_" + id
        val joystickKey = InputOverlay.JOYSTICK_PREF_KEY + "_" + id
        val recenterKey = InputOverlay.RECENTER_PREF_KEY + "_" + id

        InputOverlay.sControllerScale = preferences!!.getInt(scaleKey, 50)
        InputOverlay.sControllerAlpha = preferences!!.getInt(alphaKey, 100)
        InputOverlay.sControllerType =
            preferences!!.getInt(typeKey, InputOverlay.CONTROLLER_WIINUNCHUK)
        InputOverlay.sJoyStickSetting =
            preferences!!.getInt(joystickKey, InputOverlay.JOYSTICK_EMULATE_NONE)
        InputOverlay.sJoystickRelative =
            preferences!!.getBoolean(InputOverlay.RELATIVE_PREF_KEY, true)
        InputOverlay.sIRRecenter = preferences!!.getBoolean(recenterKey, false)

        if (isGameCubeGame) InputOverlay.sJoyStickSetting = InputOverlay.JOYSTICK_EMULATE_NONE

        InputOverlay.sSensorGCSetting = InputOverlay.SENSOR_GC_NONE
        InputOverlay.sSensorWiiSetting = InputOverlay.SENSOR_WII_NONE

        Rumble.setPhoneRumble(this, preferences!!.getBoolean(RUMBLE_PREF_KEY, true))
    }

    private fun savePreferences() {
        val id =
            if (selectedGameId!!.length > 3) selectedGameId!!.substring(0, 3) else selectedGameId!!
        val scaleKey = InputOverlay.CONTROL_SCALE_PREF_KEY + "_" + id
        val alphaKey = InputOverlay.CONTROL_ALPHA_PREF_KEY + "_" + id
        val typeKey = InputOverlay.CONTROL_TYPE_PREF_KEY + "_" + id
        val joystickKey = InputOverlay.JOYSTICK_PREF_KEY + "_" + id
        val recenterKey = InputOverlay.RECENTER_PREF_KEY + "_" + id

        val editor = preferences!!.edit()
        editor.putInt(typeKey, InputOverlay.sControllerType)
        editor.putInt(scaleKey, InputOverlay.sControllerScale)
        editor.putInt(alphaKey, InputOverlay.sControllerAlpha)
        editor.putInt(joystickKey, InputOverlay.sJoyStickSetting)
        editor.putBoolean(InputOverlay.RELATIVE_PREF_KEY, InputOverlay.sJoystickRelative)
        editor.putBoolean(recenterKey, InputOverlay.sIRRecenter)
        editor.apply()
    }

    // Gets button presses
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val input = event.device
        val button = event.keyCode
        if (button == bindingButton && input != null && bindingDevice == input.descriptor) {
            if (event.action == KeyEvent.ACTION_DOWN) onBackPressed()
            return true
        }

        if (menuVisible) return super.dispatchKeyEvent(event)

        val action: Int
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // Handling the case where the back button is pressed.
                if (button == KeyEvent.KEYCODE_BACK) {
                    onBackPressed()
                    return true
                }
                // Normal key events.
                action = NativeLibrary.ButtonState.PRESSED
            }

            KeyEvent.ACTION_UP -> action = NativeLibrary.ButtonState.RELEASED
            else -> return false
        }

        return if (input != null) NativeLibrary.onGamePadEvent(
            input.descriptor,
            button,
            action
        )
        else false
    }

    fun toggleControls() {
        val editor = preferences!!.edit()
        val controller = InputOverlay.sControllerType
        val enabledButtons = BooleanArray(16)
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.emulation_toggle_controls)

        val resId: Int
        val keyPrefix: String
        if (isGameCubeGame || controller == InputOverlay.CONTROLLER_GAMECUBE) {
            resId = R.array.gcpadButtons
            keyPrefix = "ToggleGc_"
        } else if (controller == InputOverlay.CONTROLLER_CLASSIC) {
            resId = R.array.classicButtons
            keyPrefix = "ToggleClassic_"
        } else {
            resId =
                if (controller == InputOverlay.CONTROLLER_WIINUNCHUK) R.array.nunchukButtons else R.array.wiimoteButtons
            keyPrefix = "ToggleWii_"
        }

        for (i in enabledButtons.indices) {
            enabledButtons[i] = preferences!!.getBoolean(keyPrefix + i, true)
        }
        builder.setMultiChoiceItems(
            resId, enabledButtons
        ) { _: DialogInterface?, indexSelected: Int, isChecked: Boolean ->
            editor
                .putBoolean(keyPrefix + indexSelected, isChecked)
        }

        builder.setNeutralButton(getString(R.string.emulation_toggle_all)) { _: DialogInterface?, _: Int ->
            editor.putBoolean(
                "showInputOverlay",
                !preferences!!.getBoolean("showInputOverlay", true)
            )
            editor.apply()
            emulationFragment!!.refreshInputOverlay()
        }
        builder.setPositiveButton(getString(android.R.string.ok)) { _: DialogInterface?, _: Int ->
            editor.apply()
            emulationFragment!!.refreshInputOverlay()
        }

        val alertDialog = builder.create()
        alertDialog.show()
    }

    fun adjustControls() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_input_adjust, null)

        // scale
        val seekbarScale = view.findViewById<SeekBar>(R.id.input_scale_seekbar)
        val valueScale = view.findViewById<TextView>(R.id.input_scale_value)

        seekbarScale.max = 150
        seekbarScale.progress = InputOverlay.sControllerScale
        seekbarScale.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // Do nothing
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                valueScale.text = (progress + 50).toString() + "%"
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // Do nothing
            }
        })
        valueScale.text = (seekbarScale.progress + 50).toString() + "%"

        // alpha
        val seekbarAlpha = view.findViewById<SeekBar>(R.id.input_alpha_seekbar)
        val valueAlpha = view.findViewById<TextView>(R.id.input_alpha_value)
        seekbarAlpha.max = 100
        seekbarAlpha.progress = InputOverlay.sControllerAlpha
        seekbarAlpha.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // Do nothing
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                valueAlpha.text = "$progress%"
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // Do nothing
            }
        })
        valueAlpha.text = seekbarAlpha.progress.toString() + "%"

        val builder = AlertDialog.Builder(this)
        builder.setView(view)
        builder.setOnDismissListener {
            InputOverlay.sControllerScale = seekbarScale.progress
            InputOverlay.sControllerAlpha = seekbarAlpha.progress
            emulationFragment!!.refreshInputOverlay()
        }
        builder.setNeutralButton(
            getString(R.string.emulation_control_reset_layout)
        ) { _: DialogInterface?, _: Int -> emulationFragment!!.resetCurrentLayout() }

        val alertDialog = builder.create()
        alertDialog.show()
    }

    fun changeDisc() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("*/*")
        startActivityForResult(intent, REQUEST_CHANGE_DISC)
    }

    fun chooseController() {
        val controller = InputOverlay.sControllerType
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.emulation_choose_controller)
        builder.setSingleChoiceItems(
            R.array.controllersEntries, controller
        ) { _: DialogInterface?, indexSelected: Int ->
            InputOverlay.sControllerType = indexSelected
        }
        builder.setNeutralButton(
            getString(R.string.emulation_reload_wiimote_config)
        ) { _: DialogInterface?, _: Int ->
            NativeLibrary.SetConfig(
                "WiimoteNew.ini", "Wiimote1", "Extension",
                resources.getStringArray(R.array.controllersValues)[InputOverlay.sControllerType]
            )
            emulationFragment!!.refreshInputOverlay()
            NativeLibrary.ReloadWiimoteConfig()
        }
        builder.setOnDismissListener {
            NativeLibrary.SetConfig(
                "WiimoteNew.ini", "Wiimote1", "Extension",
                resources.getStringArray(R.array.controllersValues)[InputOverlay.sControllerType]
            )
            emulationFragment!!.refreshInputOverlay()
        }

        val alertDialog = builder.create()
        alertDialog.show()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (menuVisible) {
            return false
        }

        if (((event.source and InputDevice.SOURCE_CLASS_JOYSTICK) == 0)) {
            return super.dispatchGenericMotionEvent(event)
        }

        // Don't attempt to do anything if we are disconnecting a device.
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) return true

        val input = event.device
        val motions = input.motionRanges

        for (range in motions) {
            val axis = range.axis
            val origValue = event.getAxisValue(axis)
            val value = ControllerMappingHelper.scaleAxis(input, axis, origValue)
            // If the input is still in the "flat" area, that means it's really zero.
            // This is used to compensate for imprecision in joysticks.
            if (abs(value.toDouble()) > range.flat) {
                NativeLibrary.onGamePadMoveEvent(input.descriptor, axis, value)
            } else {
                NativeLibrary.onGamePadMoveEvent(input.descriptor, axis, 0.0f)
            }
        }

        return true
    }

    val isGameCubeGame: Boolean
        get() = Platform.fromNativeInt(platform) == Platform.GAMECUBE

    fun setTouchPointer(type: Int) {
        emulationFragment!!.setTouchPointer(type)
    }

    fun updateTouchPointer() {
        emulationFragment!!.updateTouchPointer()
    }

    fun exitEmulation() {
        emulationFragment!!.stopEmulation()
        if (intent.getBooleanExtra("launchedFromShortcut", false)) {
            finishAffinity()
        } else {
            finish()
        }
    }

    fun bindSystemBack(binding: String) {
        bindingDevice = ""
        bindingButton = -1

        val descPos = binding.indexOf("Device ")
        if (descPos == -1) return

        val codePos = binding.indexOf("-Button ")
        if (codePos == -1) return

        val descriptor = binding.substring(descPos + 8, codePos - 1)
        val code = binding.substring(codePos + 8)
        bindingDevice = descriptor
        bindingButton = code.toInt()
    }

    companion object {
        private var sActivity = WeakReference<EmulationActivity?>(null)

        const val REQUEST_CHANGE_DISC = 1

        const val RUMBLE_PREF_KEY = "PhoneRumble"

        const val EXTRA_SELECTED_GAMES = "SelectedGames"
        const val EXTRA_SELECTED_TITLE = "SelectedTitle"
        const val EXTRA_SELECTED_GAMEID = "SelectedGameId"
        const val EXTRA_PLATFORM = "Platform"
        const val EXTRA_SAVED_STATE = "SavedState"

        @JvmStatic
        fun launch(context: Context, filePath: String) {
            launchFile(context, arrayOf(filePath))
        }

        @JvmStatic
        fun launch(context: Context, game: GameFile, savedState: String?) {
            val intent = Intent(context, EmulationActivity::class.java)
            intent.putExtra(EXTRA_SELECTED_GAMES, arrayOf(game.getPath()))
            intent.putExtra(EXTRA_SAVED_STATE, savedState)
            context.startActivity(intent)
        }

        @JvmStatic
        fun get(): EmulationActivity? {
            return sActivity.get()
        }

        fun launchFile(context: Context, filePaths: Array<String>) {
            val launcher = Intent(context, EmulationActivity::class.java)
            launcher.putExtra(EXTRA_SELECTED_GAMES, filePaths)

            // Try parsing a GameFile first. This should succeed for disc images.
            val gameFile = GameFile.parse(filePaths[0])
            if (gameFile != null) {
                // We don't want to pollute the game file cache with this new file,
                // so we can't just call launch() and let it handle the setup.
                launcher.putExtra(EXTRA_SELECTED_TITLE, gameFile.title)
                launcher.putExtra(EXTRA_SELECTED_GAMEID, gameFile.getGameId())
                launcher.putExtra(EXTRA_PLATFORM, gameFile.getPlatform())
            } else {
                // Display the path to the file as the game title in the menu.
                launcher.putExtra(EXTRA_SELECTED_TITLE, filePaths[0])

                // Use 00000000 as the game ID. This should match the Desktop version behavior.
                // TODO: This should really be pulled from the Core.
                launcher.putExtra(EXTRA_SELECTED_GAMEID, "00000000")

                // GameFile might be a FIFO log. Assume GameCube for the platform. It doesn't really matter
                // anyway, since this only controls the input, and the FIFO player doesn't take any input.
                launcher.putExtra(EXTRA_PLATFORM, Platform.GAMECUBE)
            }

            context.startActivity(launcher)
        }
    }
}
