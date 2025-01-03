/**
 * Copyright 2013 Dolphin Emulator Project
 * Licensed under GPLv2+
 * Refer to the license.txt file included.
 */
package org.dolphinemu.dolphinemu.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.AsyncTask
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.View.OnTouchListener
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.NativeLibrary.ButtonType
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.activities.EmulationActivity
import org.dolphinemu.dolphinemu.activities.EmulationActivity.Companion.get
import org.dolphinemu.dolphinemu.features.settings.model.Settings
import org.dolphinemu.dolphinemu.features.settings.utils.SettingsFile
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization
import kotlin.math.min

/**
 * Draws the interactive input overlay on top of the
 * [SurfaceView] that is rendering emulation.
 */
class InputOverlay(context: Context?, attrs: AttributeSet?) :
    SurfaceView(context, attrs), OnTouchListener {
    @SuppressLint("StaticFieldLeak")
    private inner class InitTask :
        AsyncTask<Context?, Void?, Map<Int, Bitmap>>() {
        override fun doInBackground(vararg contexts: Context?): Map<Int, Bitmap> {
            val settings = Settings()
            settings.loadSettings(EmulationActivity.get()!!.selectedGameId)

            val themeKeys = arrayOf(
                SettingsFile.KEY_GC_THEME,
                SettingsFile.KEY_DPAD_THEME,
                SettingsFile.KEY_JOYSTICK_THEME,
                SettingsFile.KEY_WIIMOTE_THEME,
                SettingsFile.KEY_CLASSIC_THEME
            )

            val section = settings.getSection(Settings.SECTION_INI_CORE)
            val themes: MutableMap<String, String> = HashMap()
            for (key in themeKeys) {
                val setting = section.getSetting(key)
                if (setting != null) {
                    themes[key] = setting.valueAsString
                }
            }

            val gcTheme = themes[SettingsFile.KEY_GC_THEME]
            val dpadTheme = themes[SettingsFile.KEY_DPAD_THEME]
            val joystickTheme = themes[SettingsFile.KEY_JOYSTICK_THEME]
            val wiimoteTheme = themes[SettingsFile.KEY_WIIMOTE_THEME]
            val classicTheme = themes[SettingsFile.KEY_CLASSIC_THEME]

            val gcOverlays = DirectoryInitialization.loadInputOverlay(contexts[0], gcTheme)
            val dpadOverlays = DirectoryInitialization.loadInputOverlay(contexts[0], dpadTheme)
            val joystickOverlays =
                DirectoryInitialization.loadInputOverlay(contexts[0], joystickTheme)
            val wiimoteOverlays =
                DirectoryInitialization.loadInputOverlay(contexts[0], wiimoteTheme)
            val classicOverlays =
                DirectoryInitialization.loadInputOverlay(contexts[0], classicTheme)

            val allOverlays: MutableMap<Int, Bitmap> = HashMap()
            allOverlays.putAll(gcOverlays)
            allOverlays.putAll(dpadOverlays)
            allOverlays.putAll(joystickOverlays)
            allOverlays.putAll(wiimoteOverlays)
            allOverlays.putAll(classicOverlays)

            return allOverlays
        }

        override fun onPostExecute(result: Map<Int, Bitmap>) {
            mBitmaps = result
            refreshControls()
            invalidate()
        }
    }

    private var mBitmaps: Map<Int, Bitmap>? = null
    private val mButtons = ArrayList<InputOverlayDrawableButton>()
    private val mDpads = ArrayList<InputOverlayDrawableDpad>()
    private val mJoysticks = ArrayList<InputOverlayDrawableJoystick>()
    private var mOverlayPointer: InputOverlayPointer? = null
    private var mOverlaySensor: InputOverlaySensor? = null

    private var mIsInEditMode = false
    private var mButtonBeingConfigured: InputOverlayDrawableButton? = null
    private var mDpadBeingConfigured: InputOverlayDrawableDpad? = null
    private var mJoystickBeingConfigured: InputOverlayDrawableJoystick? = null

    private val mPreferences: SharedPreferences

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        for (button in mButtons) {
            button.onDraw(canvas)
        }

        for (dpad in mDpads) {
            dpad.onDraw(canvas)
        }

        for (joystick in mJoysticks) {
            joystick.onDraw(canvas)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (isInEditMode) {
            return onTouchWhileEditing(event)
        }

        var isProcessed = false
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val pointerX = event.getX(pointerIndex)
                val pointerY = event.getY(pointerIndex)

                for (joystick in mJoysticks) {
                    if (joystick.bounds.contains(pointerX.toInt(), pointerY.toInt())) {
                        joystick.onPointerDown(pointerId, pointerX, pointerY)
                        isProcessed = true
                        break
                    }
                }

                for (button in mButtons) {
                    if (button.bounds.contains(pointerX.toInt(), pointerY.toInt())) {
                        button.onPointerDown(pointerId, pointerX, pointerY)
                        isProcessed = true
                    }
                }

                for (dpad in mDpads) {
                    if (dpad.bounds.contains(pointerX.toInt(), pointerY.toInt())) {
                        dpad.onPointerDown(pointerId, pointerX, pointerY)
                        isProcessed = true
                    }
                }

                if (!isProcessed && mOverlayPointer != null && mOverlayPointer!!.pointerId == -1) mOverlayPointer!!.onPointerDown(
                    pointerId,
                    pointerX,
                    pointerY
                )
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerCount = event.pointerCount
                var i = 0
                while (i < pointerCount) {
                    var isCaptured = false
                    val pointerId = event.getPointerId(i)
                    val pointerX = event.getX(i)
                    val pointerY = event.getY(i)

                    for (joystick in mJoysticks) {
                        if (joystick.pointerId == pointerId) {
                            joystick.onPointerMove(pointerId, pointerX, pointerY)
                            isCaptured = true
                            isProcessed = true
                            break
                        }
                    }
                    if (isCaptured) {
                        ++i
                        continue
                    }

                    for (button in mButtons) {
                        if (button.bounds.contains(pointerX.toInt(), pointerY.toInt())) {
                            if (button.pointerId == -1) {
                                button.onPointerDown(pointerId, pointerX, pointerY)
                                isProcessed = true
                            }
                        } else if (button.pointerId == pointerId) {
                            button.onPointerUp(pointerId, pointerX, pointerY)
                            isProcessed = true
                        }
                    }

                    for (dpad in mDpads) {
                        if (dpad.pointerId == pointerId) {
                            dpad.onPointerMove(pointerId, pointerX, pointerY)
                            isProcessed = true
                        }
                    }

                    if (mOverlayPointer != null && mOverlayPointer!!.pointerId == pointerId) {
                        mOverlayPointer!!.onPointerMove(pointerId, pointerX, pointerY)
                    }
                    ++i
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val pointerX = event.getX(pointerIndex)
                val pointerY = event.getY(pointerIndex)

                if (mOverlayPointer != null && mOverlayPointer!!.pointerId == pointerId) {
                    mOverlayPointer!!.onPointerUp(pointerId, pointerX, pointerY)
                }

                for (joystick in mJoysticks) {
                    if (joystick.pointerId == pointerId) {
                        joystick.onPointerUp(pointerId, pointerX, pointerY)
                        isProcessed = true
                        break
                    }
                }

                for (button in mButtons) {
                    if (button.pointerId == pointerId) {
                        button.onPointerUp(pointerId, pointerX, pointerY)
                        if (mOverlayPointer != null && button.buttonId == ButtonType.HOTKEYS_UPRIGHT_TOGGLE) {
                            mOverlayPointer!!.reset()
                        }
                        isProcessed = true
                    }
                }

                for (dpad in mDpads) {
                    if (dpad.pointerId == pointerId) {
                        dpad.onPointerUp(pointerId, pointerX, pointerY)
                        isProcessed = true
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                isProcessed = true
                if (mOverlayPointer != null) {
                    mOverlayPointer!!.onPointerUp(0, 0f, 0f)
                }

                for (joystick in mJoysticks) {
                    joystick.onPointerUp(0, 0f, 0f)
                }

                for (button in mButtons) {
                    button.onPointerUp(0, 0f, 0f)
                }

                for (dpad in mDpads) {
                    dpad.onPointerUp(0, 0f, 0f)
                }
            }
        }

        if (isProcessed) invalidate()

        return true
    }

    fun onTouchWhileEditing(event: MotionEvent): Boolean {
        val pointerIndex = event.actionIndex
        val pointerX = event.getX(pointerIndex).toInt()
        val pointerY = event.getY(pointerIndex).toInt()

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (mButtonBeingConfigured != null || mDpadBeingConfigured != null || mJoystickBeingConfigured != null) return false
                for (button in mButtons) {
                    if (button.bounds.contains(pointerX, pointerY)) {
                        mButtonBeingConfigured = button
                        mButtonBeingConfigured!!.onConfigureBegin(pointerX, pointerY)
                        return true
                    }
                }
                for (dpad in mDpads) {
                    if (dpad.bounds.contains(pointerX, pointerY)) {
                        mDpadBeingConfigured = dpad
                        mDpadBeingConfigured!!.onConfigureBegin(pointerX, pointerY)
                        return true
                    }
                }
                for (joystick in mJoysticks) {
                    if (joystick.bounds.contains(pointerX, pointerY)) {
                        mJoystickBeingConfigured = joystick
                        mJoystickBeingConfigured!!.onConfigureBegin(pointerX, pointerY)
                        return true
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (mButtonBeingConfigured != null) {
                    mButtonBeingConfigured!!.onConfigureMove(pointerX, pointerY)
                    invalidate()
                    return true
                }
                if (mDpadBeingConfigured != null) {
                    mDpadBeingConfigured!!.onConfigureMove(pointerX, pointerY)
                    invalidate()
                    return true
                }
                if (mJoystickBeingConfigured != null) {
                    mJoystickBeingConfigured!!.onConfigureMove(pointerX, pointerY)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (mButtonBeingConfigured != null) {
                    saveControlPosition(
                        mButtonBeingConfigured!!.buttonId,
                        mButtonBeingConfigured!!.bounds
                    )
                    mButtonBeingConfigured = null
                    return true
                }
                if (mDpadBeingConfigured != null) {
                    saveControlPosition(
                        mDpadBeingConfigured!!.getButtonId(0),
                        mDpadBeingConfigured!!.bounds
                    )
                    mDpadBeingConfigured = null
                    return true
                }
                if (mJoystickBeingConfigured != null) {
                    saveControlPosition(
                        mJoystickBeingConfigured!!.buttonId,
                        mJoystickBeingConfigured!!.bounds
                    )
                    mJoystickBeingConfigured = null
                    return true
                }
            }
        }

        return false
    }

    fun onSensorChanged(rotation: FloatArray) {
        if (mOverlaySensor != null) {
            mOverlaySensor!!.onSensorChanged(rotation)
        }
    }

    fun onAccuracyChanged(accuracy: Int) {
        if (mOverlaySensor == null) {
            mOverlaySensor = InputOverlaySensor()
        }
        mOverlaySensor!!.onAccuracyChanged(accuracy)
    }

    private fun addGameCubeOverlayControls() {
        var i = 0
        val buttons = arrayOf(
            intArrayOf(ButtonType.BUTTON_A, R.drawable.gcpad_a, R.drawable.gcpad_a_pressed),
            intArrayOf(ButtonType.BUTTON_B, R.drawable.gcpad_b, R.drawable.gcpad_b_pressed),
            intArrayOf(ButtonType.BUTTON_X, R.drawable.gcpad_x, R.drawable.gcpad_x_pressed),
            intArrayOf(ButtonType.BUTTON_Y, R.drawable.gcpad_y, R.drawable.gcpad_y_pressed),
            intArrayOf(ButtonType.BUTTON_Z, R.drawable.gcpad_z, R.drawable.gcpad_z_pressed),
            intArrayOf(
                ButtonType.BUTTON_START,
                R.drawable.gcpad_start,
                R.drawable.gcpad_start_pressed
            ),
            intArrayOf(ButtonType.TRIGGER_L, R.drawable.gcpad_l, R.drawable.gcpad_l_pressed),
            intArrayOf(ButtonType.TRIGGER_R, R.drawable.gcpad_r, R.drawable.gcpad_r_pressed),
            intArrayOf(
                ButtonType.TRIGGER_L_ANALOG,
                R.drawable.classic_l,
                R.drawable.classic_l_pressed
            ),
            intArrayOf(
                ButtonType.TRIGGER_R_ANALOG,
                R.drawable.classic_r,
                R.drawable.classic_r_pressed
            ),
        )
        val prefId = "ToggleGc_"
        for (button in buttons) {
            val id = button[0]
            val normal = button[1]
            val pressed = button[2]
            if (mPreferences.getBoolean(prefId + i, true)) {
                mButtons.add(initializeOverlayButton(normal, pressed, id))
            }
        }

        if (mPreferences.getBoolean(prefId + (i++), true)) {
            mDpads.add(
                initializeOverlayDpad(
                    ButtonType.BUTTON_UP, ButtonType.BUTTON_DOWN,
                    ButtonType.BUTTON_LEFT, ButtonType.BUTTON_RIGHT
                )
            )
        }
        if (mPreferences.getBoolean(prefId + (i++), true)) {
            mJoysticks.add(
                initializeOverlayJoystick(
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed, ButtonType.STICK_MAIN
                )
            )
        }
        if (mPreferences.getBoolean(prefId + (i++), true)) {
            mJoysticks.add(
                initializeOverlayJoystick(
                    R.drawable.gcpad_c,
                    R.drawable.gcpad_c_pressed, ButtonType.STICK_C
                )
            )
        }
    }

    private fun addWiimoteOverlayControls() {
        var i = 0
        val buttons = arrayOf(
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_A,
                R.drawable.wiimote_a,
                R.drawable.wiimote_a_pressed
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_B,
                R.drawable.wiimote_b,
                R.drawable.wiimote_b_pressed
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_1,
                R.drawable.wiimote_one,
                R.drawable.wiimote_one_pressed
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_2,
                R.drawable.wiimote_two,
                R.drawable.wiimote_two_pressed
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_PLUS,
                R.drawable.wiimote_plus,
                R.drawable.wiimote_plus_pressed
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_MINUS,
                R.drawable.wiimote_minus,
                R.drawable.wiimote_minus_pressed
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_HOME,
                R.drawable.wiimote_home,
                R.drawable.wiimote_home_pressed
            ),
            intArrayOf(
                ButtonType.HOTKEYS_UPRIGHT_TOGGLE,
                R.drawable.classic_x,
                R.drawable.classic_x_pressed
            ),
            intArrayOf(
                ButtonType.WIIMOTE_TILT_TOGGLE,
                R.drawable.nunchuk_z,
                R.drawable.nunchuk_z_pressed
            ),
        )
        val length = if (sControllerType == CONTROLLER_WIIREMOTE) buttons.size else buttons.size - 1
        val prefId = "ToggleWii_"
        while (i < length) {
            val id = buttons[i][0]
            val normal = buttons[i][1]
            val pressed = buttons[i][2]
            if (mPreferences.getBoolean(prefId + i, true)) {
                mButtons.add(initializeOverlayButton(normal, pressed, id))
            }
            ++i
        }

        if (mPreferences.getBoolean(prefId + (i++), true)) {
            if (sControllerType == CONTROLLER_WIINUNCHUK) {
                mDpads.add(
                    initializeOverlayDpad(
                        ButtonType.WIIMOTE_UP, ButtonType.WIIMOTE_DOWN,
                        ButtonType.WIIMOTE_LEFT, ButtonType.WIIMOTE_RIGHT
                    )
                )
            } else {
                // Horizontal Wii Remote
                mDpads.add(
                    initializeOverlayDpad(
                        ButtonType.WIIMOTE_RIGHT, ButtonType.WIIMOTE_LEFT,
                        ButtonType.WIIMOTE_UP, ButtonType.WIIMOTE_DOWN
                    )
                )
            }
        }

        if (sControllerType == CONTROLLER_WIINUNCHUK) {
            if (mPreferences.getBoolean(prefId + (i++), true)) {
                mButtons.add(
                    initializeOverlayButton(
                        R.drawable.nunchuk_c, R.drawable.nunchuk_c_pressed,
                        ButtonType.NUNCHUK_BUTTON_C
                    )
                )
            }
            if (mPreferences.getBoolean(prefId + (i++), true)) {
                mButtons.add(
                    initializeOverlayButton(
                        R.drawable.nunchuk_z, R.drawable.nunchuk_z_pressed,
                        ButtonType.NUNCHUK_BUTTON_Z
                    )
                )
            }
            if (mPreferences.getBoolean(prefId + (i++), true)) {
                mJoysticks.add(
                    initializeOverlayJoystick(
                        R.drawable.gcwii_joystick,
                        R.drawable.gcwii_joystick_pressed, ButtonType.NUNCHUK_STICK
                    )
                )
            }
        }

        // joystick emulate
        if (sJoyStickSetting != JOYSTICK_EMULATE_NONE) {
            mJoysticks.add(
                initializeOverlayJoystick(
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed, 0
                )
            )
        }
    }

    private fun addClassicOverlayControls() {
        var i = 0
        val buttons = arrayOf(
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_A,
                R.drawable.classic_a,
                R.drawable.classic_a_pressed
            ),
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_B,
                R.drawable.classic_b,
                R.drawable.classic_b_pressed
            ),
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_X,
                R.drawable.classic_x,
                R.drawable.classic_x_pressed
            ),
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_Y,
                R.drawable.classic_y,
                R.drawable.classic_y_pressed
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_1,
                R.drawable.wiimote_one,
                R.drawable.wiimote_one_pressed
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_2,
                R.drawable.wiimote_two,
                R.drawable.wiimote_two_pressed
            ),
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_PLUS,
                R.drawable.wiimote_plus,
                R.drawable.wiimote_plus_pressed
            ),
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_MINUS,
                R.drawable.wiimote_minus,
                R.drawable.wiimote_minus_pressed
            ),
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_HOME,
                R.drawable.wiimote_home,
                R.drawable.wiimote_home_pressed
            ),
            intArrayOf(
                ButtonType.CLASSIC_TRIGGER_L,
                R.drawable.classic_l,
                R.drawable.classic_l_pressed
            ),
            intArrayOf(
                ButtonType.CLASSIC_TRIGGER_R,
                R.drawable.classic_r,
                R.drawable.classic_r_pressed
            ),
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_ZL,
                R.drawable.classic_zl,
                R.drawable.classic_zl_pressed
            ),
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_ZR,
                R.drawable.classic_zr,
                R.drawable.classic_zr_pressed
            ),
        )
        val prefId = "ToggleClassic_"
        for (button in buttons) {
            val id = button[0]
            val normal = button[1]
            val pressed = button[2]
            if (mPreferences.getBoolean(prefId + i, true)) {
                mButtons.add(initializeOverlayButton(normal, pressed, id))
            }
        }

        if (mPreferences.getBoolean(prefId + (i++), true)) {
            mDpads.add(
                initializeOverlayDpad(
                    ButtonType.CLASSIC_DPAD_UP, ButtonType.CLASSIC_DPAD_DOWN,
                    ButtonType.CLASSIC_DPAD_LEFT, ButtonType.CLASSIC_DPAD_RIGHT
                )
            )
        }
        if (mPreferences.getBoolean(prefId + (i++), true)) {
            mJoysticks.add(
                initializeOverlayJoystick(
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed, ButtonType.CLASSIC_STICK_LEFT
                )
            )
        }
        if (mPreferences.getBoolean(prefId + (i++), true)) {
            mJoysticks.add(
                initializeOverlayJoystick(
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed, ButtonType.CLASSIC_STICK_RIGHT
                )
            )
        }

        // joystick emulate
        if (sJoyStickSetting != JOYSTICK_EMULATE_NONE) {
            mJoysticks.add(
                initializeOverlayJoystick(
                    R.drawable.gcwii_joystick,
                    R.drawable.gcwii_joystick_pressed, 0
                )
            )
        }
    }

    fun refreshControls() {
        if (mBitmaps == null) {
            return
        }

        // Remove all the overlay buttons
        mButtons.clear()
        mDpads.clear()
        mJoysticks.clear()

        if (mPreferences.getBoolean("showInputOverlay", true)) {
            if (get()!!.isGameCubeGame || sControllerType == CONTROLLER_GAMECUBE) {
                addGameCubeOverlayControls()
            } else if (sControllerType == CONTROLLER_CLASSIC) {
                addClassicOverlayControls()
            } else {
                addWiimoteOverlayControls()
            }
        }

        invalidate()
    }

    fun resetCurrentLayout() {
        val sPrefsEditor = mPreferences.edit()
        val res = resources

        when (controllerType) {
            CONTROLLER_GAMECUBE -> gcDefaultOverlay(sPrefsEditor, res)
            CONTROLLER_CLASSIC -> wiiClassicDefaultOverlay(sPrefsEditor, res)
            CONTROLLER_WIINUNCHUK -> wiiNunchukDefaultOverlay(sPrefsEditor, res)
            CONTROLLER_WIIREMOTE -> wiiRemoteDefaultOverlay(sPrefsEditor, res)
        }

        sPrefsEditor.apply()
        refreshControls()
    }

    fun setTouchPointer(type: Int) {
        if (type > 0) {
            if (mOverlayPointer == null) {
                val dm = context.resources.displayMetrics
                mOverlayPointer =
                    InputOverlayPointer(dm.widthPixels, dm.heightPixels, dm.scaledDensity)
            }
            mOverlayPointer!!.setType(type)
        } else {
            mOverlayPointer = null
        }
    }

    fun updateTouchPointer() {
        if (mOverlayPointer != null) {
            mOverlayPointer!!.updateTouchPointer()
        }
    }

    private fun saveControlPosition(buttonId: Int, bounds: Rect) {
        val context = context
        val dm = context.resources.displayMetrics
        val controller = controllerType
        val sPrefsEditor = mPreferences.edit()
        val x = (bounds.left + (bounds.right - bounds.left) / 2.0f) / dm.widthPixels * 2.0f - 1.0f
        val y = (bounds.top + (bounds.bottom - bounds.top) / 2.0f) / dm.heightPixels * 2.0f - 1.0f
        sPrefsEditor.putFloat(controller.toString() + "_" + buttonId + "_X", x)
        sPrefsEditor.putFloat(controller.toString() + "_" + buttonId + "_Y", y)
        sPrefsEditor.apply()
    }

    private val controllerType: Int
        get() = if (get()!!.isGameCubeGame) CONTROLLER_GAMECUBE else sControllerType

    private fun initializeOverlayButton(
        defaultResId: Int, pressedResId: Int,
        buttonId: Int
    ): InputOverlayDrawableButton {
        val context = context
        // Resources handle for fetching the initial Drawable resource.
        val res = context.resources
        // SharedPreference to retrieve the X and Y coordinates for the InputOverlayDrawableButton.
        val controller = controllerType
        // Decide scale based on button ID and user preference
        var scale: Float

        when (buttonId) {
            ButtonType.BUTTON_A, ButtonType.WIIMOTE_BUTTON_B, ButtonType.NUNCHUK_BUTTON_Z, ButtonType.BUTTON_X, ButtonType.BUTTON_Y, ButtonType.TRIGGER_L_ANALOG, ButtonType.TRIGGER_R_ANALOG -> scale =
                0.17f

            ButtonType.BUTTON_Z, ButtonType.TRIGGER_L, ButtonType.TRIGGER_R, ButtonType.CLASSIC_BUTTON_ZL, ButtonType.CLASSIC_BUTTON_ZR, ButtonType.WIIMOTE_TILT_TOGGLE -> scale =
                0.18f

            ButtonType.BUTTON_START -> scale = 0.075f
            ButtonType.WIIMOTE_BUTTON_1, ButtonType.WIIMOTE_BUTTON_2, ButtonType.WIIMOTE_BUTTON_PLUS, ButtonType.WIIMOTE_BUTTON_MINUS -> {
                scale = 0.075f
                if (controller == CONTROLLER_WIIREMOTE) scale = 0.125f
            }

            ButtonType.WIIMOTE_BUTTON_HOME, ButtonType.CLASSIC_BUTTON_PLUS, ButtonType.CLASSIC_BUTTON_MINUS, ButtonType.CLASSIC_BUTTON_HOME -> scale =
                0.075f

            ButtonType.HOTKEYS_UPRIGHT_TOGGLE -> scale = 0.0675f
            ButtonType.CLASSIC_TRIGGER_L, ButtonType.CLASSIC_TRIGGER_R -> scale = 0.22f
            ButtonType.WIIMOTE_BUTTON_A -> scale = 0.14f
            ButtonType.NUNCHUK_BUTTON_C -> scale = 0.15f
            else -> scale = 0.125f
        }

        scale *= (sControllerScale + 50).toFloat()
        scale /= 100f

        // Initialize the InputOverlayDrawableButton.
        val defaultBitmap = getInputBitmap(defaultResId, scale)
        val pressedBitmap = getInputBitmap(pressedResId, scale)
        val overlay = InputOverlayDrawableButton(
            BitmapDrawable(res, defaultBitmap), BitmapDrawable(res, pressedBitmap), buttonId
        )

        // The X and Y coordinates of the InputOverlayDrawableButton on the InputOverlay.
        // These were set in the input overlay configuration menu.
        val x = mPreferences.getFloat(controller.toString() + "_" + buttonId + "_X", 0f)
        val y = mPreferences.getFloat(controller.toString() + "_" + buttonId + "_Y", 0.5f)

        val width = defaultBitmap.width
        val height = defaultBitmap.height
        val dm = res.displayMetrics
        val drawableX = ((dm.widthPixels / 2.0f) * (1.0f + x) - width / 2.0f).toInt()
        val drawableY = ((dm.heightPixels / 2.0f) * (1.0f + y) - height / 2.0f).toInt()
        // Now set the bounds for the InputOverlayDrawableButton.
        // This will dictate where on the screen (and the what the size) the InputOverlayDrawableButton will be.
        overlay.bounds = Rect(drawableX, drawableY, drawableX + width, drawableY + height)

        // Need to set the image's position
        overlay.setPosition(drawableX, drawableY)
        overlay.setAlpha((sControllerAlpha * 255) / 100)

        return overlay
    }

    private fun initializeOverlayDpad(
        buttonUp: Int, buttonDown: Int,
        buttonLeft: Int, buttonRight: Int
    ): InputOverlayDrawableDpad {
        val defaultResId = R.drawable.gcwii_dpad
        val pressedOneDirectionResId = R.drawable.gcwii_dpad_pressed_one_direction
        val pressedTwoDirectionsResId = R.drawable.gcwii_dpad_pressed_two_directions
        // Resources handle for fetching the initial Drawable resource.
        val res = context.resources
        // SharedPreference to retrieve the X and Y coordinates for the InputOverlayDrawableDpad.
        val controller = controllerType

        // Decide scale based on button ID and user preference
        var scale = if (controller == CONTROLLER_WIIREMOTE) 0.335f else 0.275f
        scale *= (sControllerScale + 50).toFloat()
        scale /= 100f

        // Initialize the InputOverlayDrawableDpad.
        val defaultBitmap = getInputBitmap(defaultResId, scale)
        val onePressedBitmap = getInputBitmap(pressedOneDirectionResId, scale)
        val twoPressedBitmap = getInputBitmap(pressedTwoDirectionsResId, scale)
        val overlay = InputOverlayDrawableDpad(
            BitmapDrawable(res, defaultBitmap),
            BitmapDrawable(res, onePressedBitmap), BitmapDrawable(res, twoPressedBitmap),
            buttonUp, buttonDown, buttonLeft, buttonRight
        )

        // The X and Y coordinates of the InputOverlayDrawableDpad on the InputOverlay.
        // These were set in the input overlay configuration menu.
        val x = mPreferences.getFloat(controller.toString() + "_" + buttonUp + "_X", 0f)
        val y = mPreferences.getFloat(controller.toString() + "_" + buttonUp + "_Y", 0.5f)

        val width = defaultBitmap.width
        val height = defaultBitmap.height
        val dm = res.displayMetrics
        val drawableX = ((dm.widthPixels / 2.0f) * (1.0f + x) - width / 2.0f).toInt()
        val drawableY = ((dm.heightPixels / 2.0f) * (1.0f + y) - height / 2.0f).toInt()
        // Now set the bounds for the InputOverlayDrawableDpad.
        // This will dictate where on the screen (and the what the size) the InputOverlayDrawableDpad will be.
        overlay.bounds = Rect(drawableX, drawableY, drawableX + width, drawableY + height)

        // Need to set the image's position
        overlay.setPosition(drawableX, drawableY)
        overlay.setAlpha((sControllerAlpha * 255) / 100)

        return overlay
    }

    private fun initializeOverlayJoystick(
        defaultResInner: Int,
        pressedResInner: Int, joystick: Int
    ): InputOverlayDrawableJoystick {
        val context = context
        // Resources handle for fetching the initial Drawable resource.
        val res = context.resources
        // SharedPreference to retrieve the X and Y coordinates for the InputOverlayDrawableJoystick.
        val controller = controllerType
        // Decide scale based on user preference
        val scale = 0.275f * (sControllerScale + 50) / 100

        // Initialize the InputOverlayDrawableJoystick.
        val resOuter = R.drawable.gcwii_joystick_range
        val bitmapOuter = getInputBitmap(resOuter, scale)
        val bitmapInnerDefault = getInputBitmap(defaultResInner, 1.0f)
        val bitmapInnerPressed = getInputBitmap(pressedResInner, 1.0f)

        // The X and Y coordinates of the InputOverlayDrawableButton on the InputOverlay.
        // These were set in the input overlay configuration menu.
        val x = mPreferences.getFloat(controller.toString() + "_" + joystick + "_X", -0.3f)
        val y = mPreferences.getFloat(controller.toString() + "_" + joystick + "_Y", 0.3f)

        // Decide inner scale based on joystick ID
        val innerScale = if (joystick == ButtonType.STICK_C) 1.833f else 1.375f

        // Now set the bounds for the InputOverlayDrawableJoystick.
        // This will dictate where on the screen (and the what the size) the InputOverlayDrawableJoystick will be.
        val outerSize = bitmapOuter.width
        val dm = res.displayMetrics
        val drawableX = ((dm.widthPixels / 2.0f) * (1.0f + x) - outerSize / 2.0f).toInt()
        val drawableY = ((dm.heightPixels / 2.0f) * (1.0f + y) - outerSize / 2.0f).toInt()

        val outerRect = Rect(drawableX, drawableY, drawableX + outerSize, drawableY + outerSize)
        val innerRect =
            Rect(0, 0, (outerSize / innerScale).toInt(), (outerSize / innerScale).toInt())

        // Send the drawableId to the joystick so it can be referenced when saving control position.
        val overlay = InputOverlayDrawableJoystick(
            BitmapDrawable(res, bitmapOuter), BitmapDrawable(res, bitmapOuter),
            BitmapDrawable(res, bitmapInnerDefault), BitmapDrawable(res, bitmapInnerPressed),
            outerRect, innerRect, joystick
        )

        // Need to set the image's position
        overlay.setPosition(drawableX, drawableY)
        overlay.setAlpha((sControllerAlpha * 255) / 100)

        return overlay
    }

    private fun getInputBitmap(id: Int, scale: Float): Bitmap {
        // Determine the button size based on the smaller screen dimension.
        // This makes sure the buttons are the same size in both portrait and landscape.
        val res = context.resources
        val dm = res.displayMetrics
        val dimension = (min(dm.widthPixels.toDouble(), dm.heightPixels.toDouble()) * scale).toInt()
        val bitmap = mBitmaps!![id]
        var dstWidth = bitmap!!.width
        var dstHeight = bitmap.height
        if (dstWidth > dstHeight) {
            dstWidth = dstWidth * dimension / dstHeight
            dstHeight = dimension
        } else {
            dstHeight = dstHeight * dimension / dstWidth
            dstWidth = dimension
        }
        return Bitmap.createScaledBitmap(mBitmaps!![id]!!, dstWidth, dstHeight, true)
    }

    fun setIsInEditMode(isInEditMode: Boolean) {
        mIsInEditMode = isInEditMode
    }

    override fun isInEditMode(): Boolean {
        return mIsInEditMode
    }

    private fun defaultOverlay() {
        val sPrefsEditor = mPreferences.edit()
        val res = resources

        // GameCube
        gcDefaultOverlay(sPrefsEditor, res)
        // Wii Nunchuk
        wiiNunchukDefaultOverlay(sPrefsEditor, res)
        // Wii Remote
        wiiRemoteDefaultOverlay(sPrefsEditor, res)
        // Wii Classic
        wiiClassicDefaultOverlay(sPrefsEditor, res)

        sPrefsEditor.putBoolean(CONTROL_INIT_PREF_KEY, true)
        sPrefsEditor.apply()
    }

    private fun gcDefaultOverlay(sPrefsEditor: SharedPreferences.Editor, res: Resources) {
        val controller = CONTROLLER_GAMECUBE
        val buttons = arrayOf(
            intArrayOf(ButtonType.BUTTON_A, R.integer.BUTTON_A_X, R.integer.BUTTON_A_Y),
            intArrayOf(ButtonType.BUTTON_B, R.integer.BUTTON_B_X, R.integer.BUTTON_B_Y),
            intArrayOf(ButtonType.BUTTON_X, R.integer.BUTTON_X_X, R.integer.BUTTON_X_Y),
            intArrayOf(ButtonType.BUTTON_Y, R.integer.BUTTON_Y_X, R.integer.BUTTON_Y_Y),
            intArrayOf(ButtonType.BUTTON_Z, R.integer.BUTTON_Z_X, R.integer.BUTTON_Z_Y),
            intArrayOf(ButtonType.BUTTON_UP, R.integer.BUTTON_UP_X, R.integer.BUTTON_UP_Y),
            intArrayOf(ButtonType.TRIGGER_L, R.integer.TRIGGER_L_X, R.integer.TRIGGER_L_Y),
            intArrayOf(ButtonType.TRIGGER_R, R.integer.TRIGGER_R_X, R.integer.TRIGGER_R_Y),
            intArrayOf(
                ButtonType.TRIGGER_L_ANALOG,
                R.integer.TRIGGER_L_ANALOG_X,
                R.integer.TRIGGER_L_ANALOG_Y
            ),
            intArrayOf(
                ButtonType.TRIGGER_R_ANALOG,
                R.integer.TRIGGER_R_ANALOG_X,
                R.integer.TRIGGER_R_ANALOG_Y
            ),
            intArrayOf(ButtonType.BUTTON_START, R.integer.BUTTON_START_X, R.integer.BUTTON_START_Y),
            intArrayOf(ButtonType.STICK_C, R.integer.STICK_C_X, R.integer.STICK_C_Y),
            intArrayOf(ButtonType.STICK_MAIN, R.integer.STICK_MAIN_X, R.integer.STICK_MAIN_Y),
        )

        for (button in buttons) {
            val id = button[0]
            val x = button[1]
            val y = button[2]
            sPrefsEditor.putFloat(
                controller.toString() + "_" + id + "_X",
                res.getInteger(x) / 100.0f
            )
            sPrefsEditor.putFloat(
                controller.toString() + "_" + id + "_Y",
                res.getInteger(y) / 100.0f
            )
        }
    }

    private fun wiiNunchukDefaultOverlay(sPrefsEditor: SharedPreferences.Editor, res: Resources) {
        val controller = CONTROLLER_WIINUNCHUK
        val buttons = arrayOf(
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_A,
                R.integer.WIIMOTE_BUTTON_A_X,
                R.integer.WIIMOTE_BUTTON_A_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_B,
                R.integer.WIIMOTE_BUTTON_B_X,
                R.integer.WIIMOTE_BUTTON_B_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_1,
                R.integer.WIIMOTE_BUTTON_1_X,
                R.integer.WIIMOTE_BUTTON_1_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_2,
                R.integer.WIIMOTE_BUTTON_2_X,
                R.integer.WIIMOTE_BUTTON_2_Y
            ),
            intArrayOf(
                ButtonType.NUNCHUK_BUTTON_Z,
                R.integer.NUNCHUK_BUTTON_Z_X,
                R.integer.NUNCHUK_BUTTON_Z_Y
            ),
            intArrayOf(
                ButtonType.NUNCHUK_BUTTON_C,
                R.integer.NUNCHUK_BUTTON_C_X,
                R.integer.NUNCHUK_BUTTON_C_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_MINUS,
                R.integer.WIIMOTE_BUTTON_MINUS_X,
                R.integer.WIIMOTE_BUTTON_MINUS_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_PLUS,
                R.integer.WIIMOTE_BUTTON_PLUS_X,
                R.integer.WIIMOTE_BUTTON_PLUS_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_HOME,
                R.integer.WIIMOTE_BUTTON_HOME_X,
                R.integer.WIIMOTE_BUTTON_HOME_Y
            ),
            intArrayOf(ButtonType.WIIMOTE_UP, R.integer.WIIMOTE_UP_X, R.integer.WIIMOTE_UP_Y),
            intArrayOf(
                ButtonType.NUNCHUK_STICK,
                R.integer.NUNCHUK_STICK_X,
                R.integer.NUNCHUK_STICK_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_RIGHT,
                R.integer.WIIMOTE_RIGHT_X,
                R.integer.WIIMOTE_RIGHT_Y
            ),
            intArrayOf(
                ButtonType.HOTKEYS_UPRIGHT_TOGGLE,
                R.integer.WIIMOTE_BUTTON_UPRIGHT_TOGGLE_X,
                R.integer.WIIMOTE_BUTTON_UPRIGHT_TOGGLE_Y
            ),
        )

        for (button in buttons) {
            val id = button[0]
            val x = button[1]
            val y = button[2]
            sPrefsEditor.putFloat(
                controller.toString() + "_" + id + "_X",
                res.getInteger(x) / 100.0f
            )
            sPrefsEditor.putFloat(
                controller.toString() + "_" + id + "_Y",
                res.getInteger(y) / 100.0f
            )
        }
    }

    private fun wiiRemoteDefaultOverlay(sPrefsEditor: SharedPreferences.Editor, res: Resources) {
        val controller = CONTROLLER_WIIREMOTE
        val buttons = arrayOf(
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_A,
                R.integer.WIIMOTE_BUTTON_A_X,
                R.integer.WIIMOTE_BUTTON_A_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_B,
                R.integer.WIIMOTE_BUTTON_B_X,
                R.integer.WIIMOTE_BUTTON_B_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_1,
                R.integer.WIIMOTE_BUTTON_1_X,
                R.integer.WIIMOTE_BUTTON_1_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_2,
                R.integer.WIIMOTE_BUTTON_2_X,
                R.integer.WIIMOTE_BUTTON_2_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_MINUS,
                R.integer.WIIMOTE_BUTTON_MINUS_X,
                R.integer.WIIMOTE_BUTTON_MINUS_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_PLUS,
                R.integer.WIIMOTE_BUTTON_PLUS_X,
                R.integer.WIIMOTE_BUTTON_PLUS_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_HOME,
                R.integer.WIIMOTE_BUTTON_HOME_X,
                R.integer.WIIMOTE_BUTTON_HOME_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_RIGHT,
                R.integer.WIIMOTE_RIGHT_X,
                R.integer.WIIMOTE_RIGHT_Y
            ),
            intArrayOf(
                ButtonType.HOTKEYS_UPRIGHT_TOGGLE,
                R.integer.WIIMOTE_BUTTON_UPRIGHT_TOGGLE_X,
                R.integer.WIIMOTE_BUTTON_UPRIGHT_TOGGLE_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_TILT_TOGGLE,
                R.integer.WIIMOTE_BUTTON_TILT_TOGGLE_X,
                R.integer.WIIMOTE_BUTTON_TILT_TOGGLE_Y
            ),
        )

        for (button in buttons) {
            val id = button[0]
            val x = button[1]
            val y = button[2]
            sPrefsEditor.putFloat(
                controller.toString() + "_" + id + "_X",
                res.getInteger(x) / 100.0f
            )
            sPrefsEditor.putFloat(
                controller.toString() + "_" + id + "_Y",
                res.getInteger(y) / 100.0f
            )
        }
    }

    private fun wiiClassicDefaultOverlay(sPrefsEditor: SharedPreferences.Editor, res: Resources) {
        val controller = CONTROLLER_CLASSIC
        val buttons = arrayOf(
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_A,
                R.integer.CLASSIC_BUTTON_A_X,
                R.integer.CLASSIC_BUTTON_A_Y
            ),
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_B,
                R.integer.CLASSIC_BUTTON_B_X,
                R.integer.CLASSIC_BUTTON_B_Y
            ),
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_X,
                R.integer.CLASSIC_BUTTON_X_X,
                R.integer.CLASSIC_BUTTON_X_Y
            ),
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_Y,
                R.integer.CLASSIC_BUTTON_Y_X,
                R.integer.CLASSIC_BUTTON_Y_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_1,
                R.integer.CLASSIC_BUTTON_1_X,
                R.integer.CLASSIC_BUTTON_1_Y
            ),
            intArrayOf(
                ButtonType.WIIMOTE_BUTTON_2,
                R.integer.CLASSIC_BUTTON_2_X,
                R.integer.CLASSIC_BUTTON_2_Y
            ),
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_MINUS,
                R.integer.CLASSIC_BUTTON_MINUS_X,
                R.integer.CLASSIC_BUTTON_MINUS_Y
            ),
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_PLUS,
                R.integer.CLASSIC_BUTTON_PLUS_X,
                R.integer.CLASSIC_BUTTON_PLUS_Y
            ),
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_HOME,
                R.integer.CLASSIC_BUTTON_HOME_X,
                R.integer.CLASSIC_BUTTON_HOME_Y
            ),
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_ZL,
                R.integer.CLASSIC_BUTTON_ZL_X,
                R.integer.CLASSIC_BUTTON_ZL_Y
            ),
            intArrayOf(
                ButtonType.CLASSIC_BUTTON_ZR,
                R.integer.CLASSIC_BUTTON_ZR_X,
                R.integer.CLASSIC_BUTTON_ZR_Y
            ),
            intArrayOf(
                ButtonType.CLASSIC_DPAD_UP,
                R.integer.CLASSIC_DPAD_UP_X,
                R.integer.CLASSIC_DPAD_UP_Y
            ),
            intArrayOf(
                ButtonType.CLASSIC_STICK_LEFT,
                R.integer.CLASSIC_STICK_LEFT_X,
                R.integer.CLASSIC_STICK_LEFT_Y
            ),
            intArrayOf(
                ButtonType.CLASSIC_STICK_RIGHT,
                R.integer.CLASSIC_STICK_RIGHT_X,
                R.integer.CLASSIC_STICK_RIGHT_Y
            ),
            intArrayOf(
                ButtonType.CLASSIC_TRIGGER_L,
                R.integer.CLASSIC_TRIGGER_L_X,
                R.integer.CLASSIC_TRIGGER_L_Y
            ),
            intArrayOf(
                ButtonType.CLASSIC_TRIGGER_R,
                R.integer.CLASSIC_TRIGGER_R_X,
                R.integer.CLASSIC_TRIGGER_R_Y
            ),
        )

        for (button in buttons) {
            val id = button[0]
            val x = button[1]
            val y = button[2]
            sPrefsEditor.putFloat(
                controller.toString() + "_" + id + "_X",
                res.getInteger(x) / 100.0f
            )
            sPrefsEditor.putFloat(
                controller.toString() + "_" + id + "_Y",
                res.getInteger(y) / 100.0f
            )
        }
    }

    /**
     * Constructor
     *
     * @param context The current [Context].
     * @param attrs   [AttributeSet] for parsing XML attributes.
     */
    init {
        // input hack
        val gameId = get()!!.selectedGameId
        if (gameId != null && gameId.length > 3 && gameId.substring(0, 3) == "RK4") {
            sInputHackForRK4 = ButtonType.WIIMOTE_BUTTON_A
        }

        mPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (!mPreferences.getBoolean(CONTROL_INIT_PREF_KEY, false)) defaultOverlay()

        // initialize shake states
        for (i in sShakeStates.indices) {
            sShakeStates[i] = NativeLibrary.ButtonState.RELEASED
        }

        // init touch pointer
        var touchPointer = 0
        if (!get()!!.isGameCubeGame) touchPointer = mPreferences.getInt(POINTER_PREF_KEY, 0)
        setTouchPointer(touchPointer)

        InitTask().execute(context)

        // Set the on touch listener.
        setOnTouchListener(this)

        // Force draw
        setWillNotDraw(false)

        // Request focus for the overlay so it has priority on presses.
        requestFocus()
    }

    companion object {
        const val CONTROL_INIT_PREF_KEY: String = "InitOverlay"
        const val CONTROL_SCALE_PREF_KEY: String = "ControlScale"
        var sControllerScale: Int = 0
        const val CONTROL_ALPHA_PREF_KEY: String = "ControlAlpha"
        var sControllerAlpha: Int = 0

        const val POINTER_PREF_KEY: String = "TouchPointer1"
        const val RECENTER_PREF_KEY: String = "IRRecenter"
        var sIRRecenter: Boolean = false
        const val RELATIVE_PREF_KEY: String = "JoystickRelative"
        var sJoystickRelative: Boolean = false

        const val CONTROL_TYPE_PREF_KEY: String = "WiiController"
        const val CONTROLLER_GAMECUBE: Int = 0
        const val CONTROLLER_CLASSIC: Int = 1
        const val CONTROLLER_WIINUNCHUK: Int = 2
        const val CONTROLLER_WIIREMOTE: Int = 3
        var sControllerType: Int = 0

        const val JOYSTICK_PREF_KEY: String = "JoystickEmulate"
        const val JOYSTICK_EMULATE_NONE: Int = 0
        const val JOYSTICK_EMULATE_IR: Int = 1
        const val JOYSTICK_EMULATE_WII_SWING: Int = 2
        const val JOYSTICK_EMULATE_WII_TILT: Int = 3
        const val JOYSTICK_EMULATE_WII_SHAKE: Int = 4
        const val JOYSTICK_EMULATE_NUNCHUK_SWING: Int = 5
        const val JOYSTICK_EMULATE_NUNCHUK_TILT: Int = 6
        const val JOYSTICK_EMULATE_NUNCHUK_SHAKE: Int = 7
        var sJoyStickSetting: Int = 0

        const val SENSOR_GC_NONE: Int = 0
        const val SENSOR_GC_JOYSTICK: Int = 1
        const val SENSOR_GC_CSTICK: Int = 2
        const val SENSOR_GC_DPAD: Int = 3
        var sSensorGCSetting: Int = 0

        const val SENSOR_WII_NONE: Int = 0
        const val SENSOR_WII_DPAD: Int = 1
        const val SENSOR_WII_STICK: Int = 2
        const val SENSOR_WII_IR: Int = 3
        const val SENSOR_WII_SWING: Int = 4
        const val SENSOR_WII_TILT: Int = 5
        const val SENSOR_WII_SHAKE: Int = 6
        const val SENSOR_NUNCHUK_SWING: Int = 7
        const val SENSOR_NUNCHUK_TILT: Int = 8
        const val SENSOR_NUNCHUK_SHAKE: Int = 9
        var sSensorWiiSetting: Int = 0

        var sShakeStates: IntArray = IntArray(4)

        // input hack for RK4JAF
        var sInputHackForRK4: Int = -1

        @JvmField
        val ResGameCubeIds: IntArray = intArrayOf(
            // gamecube
            R.drawable.gcpad_a, R.drawable.gcpad_a_pressed,
            R.drawable.gcpad_b, R.drawable.gcpad_b_pressed,
            R.drawable.gcpad_x, R.drawable.gcpad_x_pressed,
            R.drawable.gcpad_y, R.drawable.gcpad_y_pressed,
            R.drawable.gcpad_z, R.drawable.gcpad_z_pressed,
            R.drawable.gcpad_start, R.drawable.gcpad_start_pressed,
            R.drawable.gcpad_l, R.drawable.gcpad_l_pressed,
            R.drawable.gcpad_r, R.drawable.gcpad_r_pressed,
        )
        @JvmField
        val ResGameCubeNames: Array<String> = arrayOf(
            "gcpad_a.png", "gcpad_a_pressed.png",
            "gcpad_b.png", "gcpad_b_pressed.png",
            "gcpad_x.png", "gcpad_x_pressed.png",
            "gcpad_y.png", "gcpad_y_pressed.png",
            "gcpad_z.png", "gcpad_z_pressed.png",
            "gcpad_start.png", "gcpad_start_pressed.png",
            "gcpad_l.png", "gcpad_l_pressed.png",
            "gcpad_r.png", "gcpad_r_pressed.png",
        )

        @JvmField
        val ResDpadIds: IntArray = intArrayOf(
            R.drawable.gcwii_dpad,
            R.drawable.gcwii_dpad_pressed_one_direction,
            R.drawable.gcwii_dpad_pressed_two_directions
        )
        @JvmField
        val ResDpadNames: Array<String> = arrayOf(
            "gcwii_dpad.png",
            "gcwii_dpad_pressed_one_direction.png",
            "gcwii_dpad_pressed_two_directions.png"
        )

        @JvmField
        val ResJoystickIds: IntArray = intArrayOf(
            R.drawable.gcwii_joystick,
            R.drawable.gcwii_joystick_pressed,
            R.drawable.gcwii_joystick_range,
            R.drawable.gcpad_c,
            R.drawable.gcpad_c_pressed,
        )
        @JvmField
        val ResJoystickNames: Array<String> = arrayOf(
            "gcwii_joystick.png", "gcwii_joystick_pressed.png", "gcwii_joystick_range.png",
            "gcpad_c.png", "gcpad_c_pressed.png",
        )

        @JvmField
        val ResWiimoteIds: IntArray = intArrayOf(
            // wiimote
            R.drawable.wiimote_a, R.drawable.wiimote_a_pressed,
            R.drawable.wiimote_b, R.drawable.wiimote_b_pressed,
            R.drawable.wiimote_one, R.drawable.wiimote_one_pressed,
            R.drawable.wiimote_two, R.drawable.wiimote_two_pressed,
            R.drawable.wiimote_plus, R.drawable.wiimote_plus_pressed,
            R.drawable.wiimote_minus, R.drawable.wiimote_minus_pressed,
            R.drawable.wiimote_home, R.drawable.wiimote_home_pressed,
            R.drawable.classic_x, R.drawable.classic_x_pressed,
            R.drawable.nunchuk_z, R.drawable.nunchuk_z_pressed,
            R.drawable.nunchuk_c, R.drawable.nunchuk_c_pressed,
        )
        @JvmField
        val ResWiimoteNames: Array<String> = arrayOf(
            "wiimote_a.png", "wiimote_a_pressed.png",
            "wiimote_b.png", "wiimote_b_pressed.png",
            "wiimote_one.png", "wiimote_one_pressed.png",
            "wiimote_two.png", "wiimote_two_pressed.png",
            "wiimote_plus.png", "wiimote_plus_pressed.png",
            "wiimote_minus.png", "wiimote_minus_pressed.png",
            "wiimote_home.png", "wiimote_home_pressed.png",
            "classic_x.png", "classic_x_pressed.png",
            "nunchuk_z.png", "nunchuk_z_pressed.png",
            "nunchuk_c.png", "nunchuk_c_pressed.png",
        )

        @JvmField
        val ResClassicIds: IntArray = intArrayOf(
            R.drawable.classic_a, R.drawable.classic_a_pressed,
            R.drawable.classic_b, R.drawable.classic_b_pressed,
            R.drawable.classic_x, R.drawable.classic_x_pressed,
            R.drawable.classic_y, R.drawable.classic_y_pressed,
            R.drawable.classic_zl, R.drawable.classic_zl_pressed,
            R.drawable.classic_zr, R.drawable.classic_zr_pressed,
            R.drawable.classic_l, R.drawable.classic_l_pressed,
            R.drawable.classic_r, R.drawable.classic_r_pressed,
        )
        @JvmField
        val ResClassicNames: Array<String> = arrayOf(
            "classic_a.png", "classic_a_pressed.png",
            "classic_b.png", "classic_b_pressed.png",
            "classic_x.png", "classic_x_pressed.png",
            "classic_y.png", "classic_y_pressed.png",
            "classic_zl.png", "classic_zl_pressed.png",
            "classic_zr.png", "classic_zr_pressed.png",
            "classic_l.png", "classic_l_pressed.png",
            "classic_r.png", "classic_r_pressed.png",
        )
    }
}
