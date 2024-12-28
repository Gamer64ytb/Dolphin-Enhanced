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
    InnerDefault: BitmapDrawable, InnerPressed: BitmapDrawable,
    rectOuter: Rect, rectInner: Rect, joystick: Int
) {
    private val mAxisIDs: IntArray = intArrayOf(0, 0, 0, 0)
    private val mAxises: FloatArray = floatArrayOf(0f, 0f)
    private val mFactors: FloatArray = floatArrayOf(1f, 1f, 1f, 1f)

    var pointerId: Int = -1
        private set
    var buttonId: Int = 0
        private set
    private var mControlPositionX: Int = 0
    private var mControlPositionY: Int = 0
    private var mPreviousTouchX: Int = 0
    private var mPreviousTouchY: Int = 0
    private var mAlpha: Int = 0
    private val mVirtBounds: Rect
    private val mOrigBounds: Rect
    private val mOuterBitmap: BitmapDrawable
    private val mDefaultInnerBitmap: BitmapDrawable
    private val mPressedInnerBitmap: BitmapDrawable
    private val mBoundsBoxBitmap: BitmapDrawable

    var bounds: Rect
        get() {
            return mOuterBitmap.bounds
        }
        set(bounds) {
            mOuterBitmap.bounds = bounds
        }

    init {
        setAxisIDs(joystick)

        mOuterBitmap = bitmapOuter
        mDefaultInnerBitmap = InnerDefault
        mPressedInnerBitmap = InnerPressed
        mBoundsBoxBitmap = bitmapBounds

        bounds = rectOuter
        mDefaultInnerBitmap.bounds = rectInner
        mPressedInnerBitmap.bounds = rectInner
        mVirtBounds = mOuterBitmap.copyBounds()
        mOrigBounds = mOuterBitmap.copyBounds()
        mBoundsBoxBitmap.alpha = 0
        mBoundsBoxBitmap.bounds = mVirtBounds
        updateInnerBounds()
    }

    fun onDraw(canvas: Canvas?) {
        mOuterBitmap.draw(canvas!!)
        currentBitmapDrawable.draw(canvas)
        mBoundsBoxBitmap.draw(canvas)
    }

    fun onPointerDown(id: Int, x: Float, y: Float) {
        val reCenter: Boolean = InputOverlay.sJoystickRelative
        mOuterBitmap.alpha = 0
        mBoundsBoxBitmap.alpha = mAlpha
        if (reCenter) {
            mVirtBounds.offset(x.toInt() - mVirtBounds.centerX(), y.toInt() - mVirtBounds.centerY())
        }
        mBoundsBoxBitmap.bounds = mVirtBounds
        pointerId = id

        setJoystickState(x, y)
    }

    fun onPointerMove(id: Int, x: Float, y: Float) {
        setJoystickState(x, y)
    }

    fun onPointerUp(id: Int, x: Float, y: Float) {
        mOuterBitmap.alpha = mAlpha
        mBoundsBoxBitmap.alpha = 0
        mVirtBounds.set(mOrigBounds)
        bounds = mOrigBounds
        pointerId = -1

        setJoystickState(x, y)
    }

    fun setAxisIDs(joystick: Int) {
        if (joystick != 0) {
            buttonId = joystick

            mFactors[0] = 1f
            mFactors[1] = 1f
            mFactors[2] = 1f
            mFactors[3] = 1f

            mAxisIDs[0] = joystick + 1
            mAxisIDs[1] = joystick + 2
            mAxisIDs[2] = joystick + 3
            mAxisIDs[3] = joystick + 4
            return
        }

        when (InputOverlay.sJoyStickSetting) {
            InputOverlay.JOYSTICK_EMULATE_IR -> {
                buttonId = 0

                mFactors[0] = 0.8f
                mFactors[1] = 0.8f
                mFactors[2] = 0.4f
                mFactors[3] = 0.4f

                mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_IR + 1
                mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2
                mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_IR + 3
                mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4
            }

            InputOverlay.JOYSTICK_EMULATE_WII_SWING -> {
                buttonId = 0

                mFactors[0] = -0.8f
                mFactors[1] = -0.8f
                mFactors[2] = -0.8f
                mFactors[3] = -0.8f

                mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_SWING + 1
                mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SWING + 2
                mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SWING + 3
                mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SWING + 4
            }

            InputOverlay.JOYSTICK_EMULATE_WII_TILT -> {
                buttonId = 0
                if (InputOverlay.sControllerType == InputOverlay.CONTROLLER_WIINUNCHUK) {
                    mFactors[0] = 0.8f
                    mFactors[1] = 0.8f
                    mFactors[2] = 0.8f
                    mFactors[3] = 0.8f

                    mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_TILT + 1
                    mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_TILT + 2
                    mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_TILT + 3
                    mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_TILT + 4
                } else {
                    mFactors[0] = -0.8f
                    mFactors[1] = -0.8f
                    mFactors[2] = 0.8f
                    mFactors[3] = 0.8f

                    mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_TILT + 4 // right
                    mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_TILT + 3 // left
                    mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_TILT + 1 // up
                    mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_TILT + 2 // down
                }
            }

            InputOverlay.JOYSTICK_EMULATE_WII_SHAKE -> {
                buttonId = 0
                mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_X
                mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_X
                mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Y
                mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Z
            }

            InputOverlay.JOYSTICK_EMULATE_NUNCHUK_SWING -> {
                buttonId = 0

                mFactors[0] = -0.8f
                mFactors[1] = -0.8f
                mFactors[2] = -0.8f
                mFactors[3] = -0.8f

                mAxisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_SWING + 1
                mAxisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_SWING + 2
                mAxisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_SWING + 3
                mAxisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_SWING + 4
            }

            InputOverlay.JOYSTICK_EMULATE_NUNCHUK_TILT -> {
                buttonId = 0

                mFactors[0] = 0.8f
                mFactors[1] = 0.8f
                mFactors[2] = 0.8f
                mFactors[3] = 0.8f

                mAxisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_TILT + 1
                mAxisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_TILT + 2
                mAxisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_TILT + 3
                mAxisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_TILT + 4
            }

            InputOverlay.JOYSTICK_EMULATE_NUNCHUK_SHAKE -> {
                buttonId = 0
                mAxisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_X
                mAxisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_X
                mAxisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_Y
                mAxisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_Z
            }
        }
    }

    private fun setJoystickState(touchX: Float, touchY: Float) {
        var touchX: Float = touchX
        var touchY: Float = touchY
        if (pointerId != -1) {
            var maxY: Float = mVirtBounds.bottom.toFloat()
            var maxX: Float = mVirtBounds.right.toFloat()
            touchX -= mVirtBounds.centerX().toFloat()
            maxX -= mVirtBounds.centerX().toFloat()
            touchY -= mVirtBounds.centerY().toFloat()
            maxY -= mVirtBounds.centerY().toFloat()
            val AxisX: Float = touchX / maxX
            val AxisY: Float = touchY / maxY
            mAxises[0] = AxisY
            mAxises[1] = AxisX
        } else {
            mAxises[1] = 0.0f
            mAxises[0] = mAxises[1]
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
                NativeLibrary.TouchScreenDevice, mAxisIDs[i], mFactors[i] * axises[i]
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
                        NativeLibrary.TouchScreenDevice, mAxisIDs[i],
                        NativeLibrary.ButtonState.PRESSED
                    )
                }
            } else if (InputOverlay.sShakeStates[i] != NativeLibrary.ButtonState.RELEASED) {
                InputOverlay.sShakeStates[i] = NativeLibrary.ButtonState.RELEASED
                NativeLibrary.onGamePadEvent(
                    NativeLibrary.TouchScreenDevice, mAxisIDs[i],
                    NativeLibrary.ButtonState.RELEASED
                )
            }
        }
    }

    fun onConfigureBegin(x: Int, y: Int) {
        mPreviousTouchX = x
        mPreviousTouchY = y
    }

    fun onConfigureMove(x: Int, y: Int) {
        val deltaX: Int = x - mPreviousTouchX
        val deltaY: Int = y - mPreviousTouchY
        val bounds: Rect = bounds
        mControlPositionX += deltaX
        mControlPositionY += deltaY
        this.bounds = Rect(
            mControlPositionX, mControlPositionY,
            mControlPositionX + bounds.width(),
            mControlPositionY + bounds.height()
        )
        mVirtBounds.set(
            mControlPositionX, mControlPositionY,
            mControlPositionX + mVirtBounds.width(),
            mControlPositionY + mVirtBounds.height()
        )
        updateInnerBounds()
        mOrigBounds.set(
            mControlPositionX, mControlPositionY,
            mControlPositionX + mOrigBounds.width(),
            mControlPositionY + mOrigBounds.height()
        )
        mPreviousTouchX = x
        mPreviousTouchY = y
    }

    val axisValues: FloatArray
        get() = floatArrayOf(
            mAxises[0],
            mAxises[0],
            mAxises[1],
            mAxises[1]
        )

    private fun updateInnerBounds() {
        var X: Int = mVirtBounds.centerX() + ((mAxises[1]) * (mVirtBounds.width() / 2)).toInt()
        var Y: Int = mVirtBounds.centerY() + ((mAxises[0]) * (mVirtBounds.height() / 2)).toInt()

        if (X > mVirtBounds.centerX() + (mVirtBounds.width() / 2)) X =
            mVirtBounds.centerX() + (mVirtBounds.width() / 2)
        if (X < mVirtBounds.centerX() - (mVirtBounds.width() / 2)) X =
            mVirtBounds.centerX() - (mVirtBounds.width() / 2)
        if (Y > mVirtBounds.centerY() + (mVirtBounds.height() / 2)) Y =
            mVirtBounds.centerY() + (mVirtBounds.height() / 2)
        if (Y < mVirtBounds.centerY() - (mVirtBounds.height() / 2)) Y =
            mVirtBounds.centerY() - (mVirtBounds.height() / 2)

        val width: Int = mPressedInnerBitmap.bounds.width() / 2
        val height: Int = mPressedInnerBitmap.bounds.height() / 2
        mDefaultInnerBitmap.setBounds(X - width, Y - height, X + width, Y + height)
        mPressedInnerBitmap.bounds = mDefaultInnerBitmap.bounds
    }

    fun setPosition(x: Int, y: Int) {
        mControlPositionX = x
        mControlPositionY = y
    }

    private val currentBitmapDrawable: BitmapDrawable
        get() {
            return if (pointerId != -1) mPressedInnerBitmap else mDefaultInnerBitmap
        }

    fun setAlpha(value: Int) {
        mAlpha = value

        mDefaultInnerBitmap.alpha = value
        mPressedInnerBitmap.alpha = value

        if (pointerId == -1) {
            mOuterBitmap.alpha = value
            mBoundsBoxBitmap.alpha = 0
        } else {
            mOuterBitmap.alpha = 0
            mBoundsBoxBitmap.alpha = value
        }
    }
}
