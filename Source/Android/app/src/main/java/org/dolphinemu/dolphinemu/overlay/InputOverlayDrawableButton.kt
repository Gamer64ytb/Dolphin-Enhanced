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
    private val mDefaultStateBitmap: BitmapDrawable,
    private val mPressedStateBitmap: BitmapDrawable, // The ID identifying what type of button this Drawable represents.
    val buttonId: Int
) {
    var pointerId: Int
        private set
    private var mTiltStatus = 0
    private var mPreviousTouchX = 0
    private var mPreviousTouchY = 0
    private var mControlPositionX = 0
    private var mControlPositionY = 0
    private var mHandler: Handler? = null

    init {
        pointerId = -1

        // input hack
        if (buttonId == InputOverlay.sInputHackForRK4) {
            mHandler = Handler()
        }
    }

    fun onConfigureBegin(x: Int, y: Int) {
        mPreviousTouchX = x
        mPreviousTouchY = y
    }

    fun onConfigureMove(x: Int, y: Int) {
        val bounds = bounds
        mControlPositionX += x - mPreviousTouchX
        mControlPositionY += y - mPreviousTouchY
        this.bounds = Rect(
            mControlPositionX, mControlPositionY,
            mControlPositionX + bounds.width(), mControlPositionY + bounds.height()
        )
        mPreviousTouchX = x
        mPreviousTouchY = y
    }

    fun onDraw(canvas: Canvas?) {
        currentStateBitmapDrawable.draw(canvas!!)
    }

    fun onPointerDown(id: Int, x: Float, y: Float) {
        pointerId = id
        if (buttonId == NativeLibrary.ButtonType.WIIMOTE_TILT_TOGGLE) {
            val valueList = floatArrayOf(0.5f, 1.0f, 0.0f)
            val value = valueList[mTiltStatus]
            mTiltStatus = (mTiltStatus + 1) % valueList.size
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
                mHandler!!.postDelayed({
                    NativeLibrary.onGamePadMoveEvent(
                        NativeLibrary.TouchScreenDevice,
                        NativeLibrary.ButtonType.WIIMOTE_SHAKE_X + 2,
                        1f
                    )
                    mHandler!!.postDelayed({
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
        mControlPositionX = x
        mControlPositionY = y
    }

    private val currentStateBitmapDrawable: BitmapDrawable
        get() = if (pointerId != -1) mPressedStateBitmap else mDefaultStateBitmap

    fun setAlpha(value: Int) {
        mDefaultStateBitmap.alpha = value
        mPressedStateBitmap.alpha = value
    }

    var bounds: Rect
        get() = mDefaultStateBitmap.bounds
        set(bounds) {
            mDefaultStateBitmap.bounds = bounds
            mPressedStateBitmap.bounds = bounds
        }
}
