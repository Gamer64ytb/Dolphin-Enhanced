/**
 * Copyright 2013 Dolphin Emulator Project
 * Licensed under GPLv2+
 * Refer to the license.txt file included.
 */
package org.dolphinemu.dolphinemu.overlay

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import org.dolphinemu.dolphinemu.NativeLibrary

class InputOverlayDrawableButton
    (
    private val defaultStateBitmap: BitmapDrawable,
    private val pressedStateBitmap: BitmapDrawable, // The ID identifying what type of button this Drawable represents.
    val buttonId: Int
) {
    var pointerId: Int
        private set
    private var tiltStatus = 0
    private var previousTouchX = 0
    private var previousTouchY = 0
    private var controlPositionX = 0
    private var controlPositionY = 0
    private var handler: Handler? = null

    init {
        pointerId = -1

        // input hack
        if (buttonId == InputOverlay.sInputHackForRK4) {
            handler = Handler()
        }
    }

    fun onConfigureBegin(x: Int, y: Int) {
        previousTouchX = x
        previousTouchY = y
    }

    fun onConfigureMove(x: Int, y: Int) {
        val bounds = bounds
        controlPositionX += x - previousTouchX
        controlPositionY += y - previousTouchY
        this.bounds = Rect(
            controlPositionX, controlPositionY,
            controlPositionX + bounds.width(), controlPositionY + bounds.height()
        )
        previousTouchX = x
        previousTouchY = y
    }

    fun onDraw(canvas: Canvas?) {
        currentStateBitmapDrawable.draw(canvas!!)
    }

    fun onPointerDown(id: Int, x: Float, y: Float) {
        pointerId = id
        if (buttonId == NativeLibrary.ButtonType.WIIMOTE_TILT_TOGGLE) {
            val valueList = floatArrayOf(0.5f, 1.0f, 0.0f)
            val value = valueList[tiltStatus]
            tiltStatus = (tiltStatus + 1) % valueList.size
            NativeLibrary.onGamePadMoveEvent(
                NativeLibrary.TouchScreenDevice,
                NativeLibrary.ButtonType.WIIMOTE_TILT + 1,
                value
            )
            NativeLibrary.onGamePadMoveEvent(
                NativeLibrary.TouchScreenDevice,
                NativeLibrary.ButtonType.WIIMOTE_TILT + 2,
                value
            )
            NativeLibrary.onGamePadMoveEvent(
                NativeLibrary.TouchScreenDevice,
                NativeLibrary.ButtonType.WIIMOTE_TILT + 3,
                0f
            )
            NativeLibrary.onGamePadMoveEvent(
                NativeLibrary.TouchScreenDevice,
                NativeLibrary.ButtonType.WIIMOTE_TILT + 4,
                0f
            )
        } else {
            NativeLibrary.onGamePadEvent(
                NativeLibrary.TouchScreenDevice,
                buttonId, NativeLibrary.ButtonState.PRESSED
            )
        }
    }

    fun onPointerMove(id: Int, x: Float, y: Float) {
    }

    fun onPointerUp(id: Int, x: Float, y: Float) {
        pointerId = -1
        if (buttonId != NativeLibrary.ButtonType.WIIMOTE_TILT_TOGGLE) {
            NativeLibrary.onGamePadEvent(
                NativeLibrary.TouchScreenDevice,
                buttonId, NativeLibrary.ButtonState.RELEASED
            )
            if (buttonId == InputOverlay.sInputHackForRK4) {
                handler!!.postDelayed({
                    NativeLibrary.onGamePadMoveEvent(
                        NativeLibrary.TouchScreenDevice,
                        NativeLibrary.ButtonType.WIIMOTE_SHAKE_X + 2,
                        1f
                    )
                    handler!!.postDelayed({
                        NativeLibrary.onGamePadMoveEvent(
                            NativeLibrary.TouchScreenDevice,
                            NativeLibrary.ButtonType.WIIMOTE_SHAKE_X + 2,
                            0f
                        )
                    }, 60)
                }, 120)
            }
        }
    }

    fun setPosition(x: Int, y: Int) {
        controlPositionX = x
        controlPositionY = y
    }

    private val currentStateBitmapDrawable: BitmapDrawable
        get() = if (pointerId != -1) pressedStateBitmap else defaultStateBitmap

    fun setAlpha(value: Int) {
        defaultStateBitmap.alpha = value
        pressedStateBitmap.alpha = value
    }

    var bounds: Rect
        get() = defaultStateBitmap.bounds
        set(bounds) {
            defaultStateBitmap.bounds = bounds
            pressedStateBitmap.bounds = bounds
        }
}
