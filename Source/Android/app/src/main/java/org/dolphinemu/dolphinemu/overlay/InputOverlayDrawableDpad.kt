/**
 * Copyright 2016 Dolphin Emulator Project
 * Licensed under GPLv2+
 * Refer to the license.txt file included.
 */
package org.dolphinemu.dolphinemu.overlay

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import org.dolphinemu.dolphinemu.NativeLibrary

class InputOverlayDrawableDpad
    (
    private val mDefaultStateBitmap: BitmapDrawable,
    private val mPressedOneDirectionStateBitmap: BitmapDrawable,
    private val mPressedTwoDirectionsStateBitmap: BitmapDrawable,
    buttonUp: Int,
    buttonDown: Int,
    buttonLeft: Int,
    buttonRight: Int
) {
    // The ID identifying what type of button this Drawable represents.
    private val mButtonIds = IntArray(4)
    private val mPressStates = BooleanArray(4)
    var pointerId: Int
        private set
    private var mPreviousTouchX = 0
    private var mPreviousTouchY = 0
    private var mControlPositionX = 0
    private var mControlPositionY = 0

    init {
        pointerId = -1

        mButtonIds[0] = buttonUp
        mButtonIds[1] = buttonDown
        mButtonIds[2] = buttonLeft
        mButtonIds[3] = buttonRight

        mPressStates[0] = false
        mPressStates[1] = false
        mPressStates[2] = false
        mPressStates[3] = false
    }

    fun onDraw(canvas: Canvas) {
        val bounds = bounds
        val px = mControlPositionX + (bounds.width() / 2)
        val py = mControlPositionY + (bounds.height() / 2)

        val up = mPressStates[0]
        val down = mPressStates[1]
        val left = mPressStates[2]
        val right = mPressStates[3]

        if (up) {
            if (left) mPressedTwoDirectionsStateBitmap.draw(canvas)
            else if (right) {
                canvas.save()
                canvas.rotate(90f, px.toFloat(), py.toFloat())
                mPressedTwoDirectionsStateBitmap.draw(canvas)
                canvas.restore()
            } else mPressedOneDirectionStateBitmap.draw(canvas)
        } else if (down) {
            if (left) {
                canvas.save()
                canvas.rotate(270f, px.toFloat(), py.toFloat())
                mPressedTwoDirectionsStateBitmap.draw(canvas)
                canvas.restore()
            } else if (right) {
                canvas.save()
                canvas.rotate(180f, px.toFloat(), py.toFloat())
                mPressedTwoDirectionsStateBitmap.draw(canvas)
                canvas.restore()
            } else {
                canvas.save()
                canvas.rotate(180f, px.toFloat(), py.toFloat())
                mPressedOneDirectionStateBitmap.draw(canvas)
                canvas.restore()
            }
        } else if (left) {
            canvas.save()
            canvas.rotate(270f, px.toFloat(), py.toFloat())
            mPressedOneDirectionStateBitmap.draw(canvas)
            canvas.restore()
        } else if (right) {
            canvas.save()
            canvas.rotate(90f, px.toFloat(), py.toFloat())
            mPressedOneDirectionStateBitmap.draw(canvas)
            canvas.restore()
        } else {
            mDefaultStateBitmap.draw(canvas)
        }
    }

    fun getButtonId(direction: Int): Int {
        return mButtonIds[direction]
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

    fun onPointerDown(id: Int, x: Float, y: Float) {
        pointerId = id
        setDpadState(x.toInt(), y.toInt())
    }

    fun onPointerMove(id: Int, x: Float, y: Float) {
        setDpadState(x.toInt(), y.toInt())
    }

    fun onPointerUp(id: Int, x: Float, y: Float) {
        pointerId = -1
        setDpadState(x.toInt(), y.toInt())
    }

    fun setPosition(x: Int, y: Int) {
        mControlPositionX = x
        mControlPositionY = y
    }

    var bounds: Rect
        get() = mDefaultStateBitmap.bounds
        set(bounds) {
            mDefaultStateBitmap.bounds = bounds
            mPressedOneDirectionStateBitmap.bounds = bounds
            mPressedTwoDirectionsStateBitmap.bounds = bounds
        }

    fun setAlpha(value: Int) {
        mDefaultStateBitmap.alpha = value
        mPressedOneDirectionStateBitmap.alpha = value
        mPressedTwoDirectionsStateBitmap.alpha = value
    }

    private fun setDpadState(pointerX: Int, pointerY: Int) {
        // Up, Down, Left, Right
        val pressed = booleanArrayOf(false, false, false, false)

        if (pointerId != -1) {
            val bounds = bounds

            if (bounds.top + (bounds.height() / 3) > pointerY) pressed[0] = true
            else if (bounds.bottom - (bounds.height() / 3) < pointerY) pressed[1] = true
            if (bounds.left + (bounds.width() / 3) > pointerX) pressed[2] = true
            else if (bounds.right - (bounds.width() / 3) < pointerX) pressed[3] = true
        }

        for (i in pressed.indices) {
            if (pressed[i] != mPressStates[i]) {
                NativeLibrary
                    .onGamePadEvent(
                        NativeLibrary.TouchScreenDevice,
                        mButtonIds[i],
                        if (pressed[i]) NativeLibrary.ButtonState.PRESSED else NativeLibrary.ButtonState.RELEASED
                    )
            }
        }

        mPressStates[0] = pressed[0]
        mPressStates[1] = pressed[1]
        mPressStates[2] = pressed[2]
        mPressStates[3] = pressed[3]
    }
}
