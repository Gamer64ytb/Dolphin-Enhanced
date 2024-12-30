/**
 * Copyright 2013 Dolphin Emulator Project
 * Licensed under GPLv2+
 * Refer to the license.txt file included.
 */
package org.dolphinemu.dolphinemu.overlay

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import org.dolphinemu.dolphinemu.NativeLibrary
import kotlin.math.min

class InputOverlayDrawableJoystick
    (
    bitmapBounds: BitmapDrawable, bitmapOuter: BitmapDrawable,
    innerDefault: BitmapDrawable, innerPressed: BitmapDrawable,
    rectOuter: Rect, rectInner: Rect, joystick: Int
) {
    private val axisIDs: IntArray = intArrayOf(0, 0, 0, 0)
    private val axises: FloatArray = floatArrayOf(0f, 0f)
    private val factors: FloatArray = floatArrayOf(1f, 1f, 1f, 1f)

    var pointerId = -1
        private set
    var buttonId = 0
        private set
    private var controlPositionX = 0
    private var controlPositionY = 0
    private var previousTouchX = 0
    private var previousTouchY = 0
    private var alpha = 0
    private val virtBounds: Rect
    private val origBounds: Rect
    private val outerBitmap: BitmapDrawable
    private val defaultInnerBitmap: BitmapDrawable
    private val pressedInnerBitmap: BitmapDrawable
    private val boundsBoxBitmap: BitmapDrawable

    var bounds: Rect
        get() {
            return outerBitmap.bounds
        }
        set(bounds) {
            outerBitmap.bounds = bounds
        }

    init {
        setAxisIDs(joystick)

        outerBitmap = bitmapOuter
        defaultInnerBitmap = innerDefault
        pressedInnerBitmap = innerPressed
        boundsBoxBitmap = bitmapBounds

        bounds = rectOuter
        defaultInnerBitmap.bounds = rectInner
        pressedInnerBitmap.bounds = rectInner
        virtBounds = outerBitmap.copyBounds()
        origBounds = outerBitmap.copyBounds()
        boundsBoxBitmap.alpha = 0
        boundsBoxBitmap.bounds = virtBounds
        updateInnerBounds()
    }

    fun onDraw(canvas: Canvas?) {
        outerBitmap.draw(canvas!!)
        currentBitmapDrawable.draw(canvas)
        boundsBoxBitmap.draw(canvas)
    }

    fun onPointerDown(id: Int, x: Float, y: Float) {
        val reCenter = InputOverlay.sJoystickRelative
        outerBitmap.alpha = 0
        boundsBoxBitmap.alpha = alpha
        if (reCenter) {
            virtBounds.offset(x.toInt() - virtBounds.centerX(), y.toInt() - virtBounds.centerY())
        }
        boundsBoxBitmap.bounds = virtBounds
        pointerId = id

        setJoystickState(x, y)
    }

    fun onPointerMove(id: Int, x: Float, y: Float) {
        setJoystickState(x, y)
    }

    fun onPointerUp(id: Int, x: Float, y: Float) {
        outerBitmap.alpha = alpha
        boundsBoxBitmap.alpha = 0
        virtBounds.set(origBounds)
        bounds = origBounds
        pointerId = -1

        setJoystickState(x, y)
    }

    private fun setAxisIDs(joystick: Int) {
        if (joystick != 0) {
            buttonId = joystick

            factors[0] = 1f
            factors[1] = 1f
            factors[2] = 1f
            factors[3] = 1f

            axisIDs[0] = joystick + 1
            axisIDs[1] = joystick + 2
            axisIDs[2] = joystick + 3
            axisIDs[3] = joystick + 4
            return
        }

        when (InputOverlay.sJoyStickSetting) {
            InputOverlay.JOYSTICK_EMULATE_IR -> {
                buttonId = 0

                factors[0] = 0.8f
                factors[1] = 0.8f
                factors[2] = 0.4f
                factors[3] = 0.4f

                axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_IR + 1
                axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2
                axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_IR + 3
                axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4
            }

            InputOverlay.JOYSTICK_EMULATE_WII_SWING -> {
                buttonId = 0

                factors[0] = -0.8f
                factors[1] = -0.8f
                factors[2] = -0.8f
                factors[3] = -0.8f

                axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_SWING + 1
                axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SWING + 2
                axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SWING + 3
                axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SWING + 4
            }

            InputOverlay.JOYSTICK_EMULATE_WII_TILT -> {
                buttonId = 0
                if (InputOverlay.sControllerType == InputOverlay.CONTROLLER_WIINUNCHUK) {
                    factors[0] = 0.8f
                    factors[1] = 0.8f
                    factors[2] = 0.8f
                    factors[3] = 0.8f

                    axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_TILT + 1
                    axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_TILT + 2
                    axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_TILT + 3
                    axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_TILT + 4
                } else {
                    factors[0] = -0.8f
                    factors[1] = -0.8f
                    factors[2] = 0.8f
                    factors[3] = 0.8f

                    axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_TILT + 4 // right
                    axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_TILT + 3 // left
                    axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_TILT + 1 // up
                    axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_TILT + 2 // down
                }
            }

            InputOverlay.JOYSTICK_EMULATE_WII_SHAKE -> {
                buttonId = 0
                axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_X
                axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_X
                axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Y
                axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Z
            }

            InputOverlay.JOYSTICK_EMULATE_NUNCHUK_SWING -> {
                buttonId = 0

                factors[0] = -0.8f
                factors[1] = -0.8f
                factors[2] = -0.8f
                factors[3] = -0.8f

                axisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_SWING + 1
                axisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_SWING + 2
                axisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_SWING + 3
                axisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_SWING + 4
            }

            InputOverlay.JOYSTICK_EMULATE_NUNCHUK_TILT -> {
                buttonId = 0

                factors[0] = 0.8f
                factors[1] = 0.8f
                factors[2] = 0.8f
                factors[3] = 0.8f

                axisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_TILT + 1
                axisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_TILT + 2
                axisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_TILT + 3
                axisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_TILT + 4
            }

            InputOverlay.JOYSTICK_EMULATE_NUNCHUK_SHAKE -> {
                buttonId = 0
                axisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_X
                axisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_X
                axisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_Y
                axisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_Z
            }
        }
    }

    private fun setJoystickState(touchX: Float, touchY: Float) {
        var touchX: Float = touchX
        var touchY: Float = touchY
        if (pointerId != -1) {
            var maxY: Float = virtBounds.bottom.toFloat()
            var maxX: Float = virtBounds.right.toFloat()
            touchX -= virtBounds.centerX().toFloat()
            maxX -= virtBounds.centerX().toFloat()
            touchY -= virtBounds.centerY().toFloat()
            maxY -= virtBounds.centerY().toFloat()
            val axisX: Float = touchX / maxX
            val axisY: Float = touchY / maxY
            axises[0] = axisY
            axises[1] = axisX
        } else {
            axises[1] = 0.0f
            axises[0] = axises[1]
        }

        updateInnerBounds()

        val axises: FloatArray = axisValues

        if (buttonId != 0) {
            // fx wii classic or classic bind
            axises[1] = min(axises[1].toDouble(), 1.0).toFloat()
            axises[0] = min(axises[0].toDouble(), 0.0).toFloat()
            axises[3] = min(axises[3].toDouble(), 1.0).toFloat()
            axises[2] = min(axises[2].toDouble(), 0.0).toFloat()
        } else if (InputOverlay.sJoyStickSetting == InputOverlay.JOYSTICK_EMULATE_WII_SHAKE ||
            InputOverlay.sJoyStickSetting == InputOverlay.JOYSTICK_EMULATE_NUNCHUK_SHAKE
        ) {
            // shake
            axises[0] = -axises[1]
            axises[1] = -axises[1]
            axises[3] = -axises[3]
            handleShakeEvent(axises)
            return
        }

        for (i in 0..3) {
            NativeLibrary.onGamePadMoveEvent(
                NativeLibrary.TouchScreenDevice, axisIDs[i], factors[i] * axises[i]
            )
        }
    }

    // axis to button
    private fun handleShakeEvent(axises: FloatArray) {
        for (i in axises.indices) {
            if (axises[i] > 0.15f) {
                if (InputOverlay.sShakeStates[i] != NativeLibrary.ButtonState.PRESSED) {
                    InputOverlay.sShakeStates[i] = NativeLibrary.ButtonState.PRESSED
                    NativeLibrary.onGamePadEvent(
                        NativeLibrary.TouchScreenDevice, axisIDs[i],
                        NativeLibrary.ButtonState.PRESSED
                    )
                }
            } else if (InputOverlay.sShakeStates[i] != NativeLibrary.ButtonState.RELEASED) {
                InputOverlay.sShakeStates[i] = NativeLibrary.ButtonState.RELEASED
                NativeLibrary.onGamePadEvent(
                    NativeLibrary.TouchScreenDevice, axisIDs[i],
                    NativeLibrary.ButtonState.RELEASED
                )
            }
        }
    }

    fun onConfigureBegin(x: Int, y: Int) {
        previousTouchX = x
        previousTouchY = y
    }

    fun onConfigureMove(x: Int, y: Int) {
        val deltaX = x - previousTouchX
        val deltaY = y - previousTouchY
        val bounds: Rect = bounds
        controlPositionX += deltaX
        controlPositionY += deltaY
        this.bounds = Rect(
            controlPositionX, controlPositionY,
            controlPositionX + bounds.width(),
            controlPositionY + bounds.height()
        )
        virtBounds.set(
            controlPositionX, controlPositionY,
            controlPositionX + virtBounds.width(),
            controlPositionY + virtBounds.height()
        )
        updateInnerBounds()
        origBounds.set(
            controlPositionX, controlPositionY,
            controlPositionX + origBounds.width(),
            controlPositionY + origBounds.height()
        )
        previousTouchX = x
        previousTouchY = y
    }

    private val axisValues: FloatArray
        get() = floatArrayOf(
            axises[0],
            axises[0],
            axises[1],
            axises[1]
        )

    private fun updateInnerBounds() {
        var x = virtBounds.centerX() + ((axises[1]) * (virtBounds.width() / 2)).toInt()
        var y = virtBounds.centerY() + ((axises[0]) * (virtBounds.height() / 2)).toInt()

        if (x > virtBounds.centerX() + (virtBounds.width() / 2)) x =
            virtBounds.centerX() + (virtBounds.width() / 2)
        if (x < virtBounds.centerX() - (virtBounds.width() / 2)) x =
            virtBounds.centerX() - (virtBounds.width() / 2)
        if (y > virtBounds.centerY() + (virtBounds.height() / 2)) y =
            virtBounds.centerY() + (virtBounds.height() / 2)
        if (y < virtBounds.centerY() - (virtBounds.height() / 2)) y =
            virtBounds.centerY() - (virtBounds.height() / 2)

        val width = pressedInnerBitmap.bounds.width() / 2
        val height = pressedInnerBitmap.bounds.height() / 2
        defaultInnerBitmap.setBounds(x - width, y - height, x + width, y + height)
        pressedInnerBitmap.bounds = defaultInnerBitmap.bounds
    }

    fun setPosition(x: Int, y: Int) {
        controlPositionX = x
        controlPositionY = y
    }

    private val currentBitmapDrawable: BitmapDrawable
        get() {
            return if (pointerId != -1) pressedInnerBitmap else defaultInnerBitmap
        }

    fun setAlpha(value: Int) {
        alpha = value

        defaultInnerBitmap.alpha = value
        pressedInnerBitmap.alpha = value

        if (pointerId == -1) {
            outerBitmap.alpha = value
            boundsBoxBitmap.alpha = 0
        } else {
            outerBitmap.alpha = 0
            boundsBoxBitmap.alpha = value
        }
    }
}
