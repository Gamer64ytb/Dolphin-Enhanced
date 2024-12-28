package org.dolphinemu.dolphinemu.overlay

import org.dolphinemu.dolphinemu.NativeLibrary

class InputOverlayPointer
    (width: Int, height: Int, scaledDensity: Float) {
    private var mType: Int
    var pointerId: Int
        private set

    private var mDisplayScale: Float
    private val mScaledDensity: Float
    private val mMaxWidth: Int
    private val mMaxHeight: Int
    private var mGameWidthHalf: Float
    private var mGameHeightHalf: Float
    private var mAdjustX: Float
    private var mAdjustY: Float

    private val mAxisIDs = IntArray(4)

    // used for stick
    private val mAxises = FloatArray(4)
    private var mCenterX = 0f
    private var mCenterY = 0f

    // used for click
    private var mLastClickTime: Long
    private val mClickButtonId: Int
    private var mIsDoubleClick: Boolean

    init {
        mType = TYPE_OFF
        mAxisIDs[0] = 0
        mAxisIDs[1] = 0
        mAxisIDs[2] = 0
        mAxisIDs[3] = 0

        mDisplayScale = 1.0f
        mScaledDensity = scaledDensity
        mMaxWidth = width
        mMaxHeight = height
        mGameWidthHalf = width / 2.0f
        mGameHeightHalf = height / 2.0f
        mAdjustX = 1.0f
        mAdjustY = 1.0f

        pointerId = -1

        mLastClickTime = 0
        mIsDoubleClick = false
        mClickButtonId = NativeLibrary.ButtonType.WIIMOTE_BUTTON_A

        if (NativeLibrary.IsRunning()) {
            updateTouchPointer()
        }
    }

    fun updateTouchPointer() {
        val deviceAR = mMaxWidth.toFloat() / mMaxHeight.toFloat()
        val gameAR = NativeLibrary.GetGameAspectRatio()
        // same scale ratio in renderbase.cpp
        mDisplayScale = (NativeLibrary.GetGameDisplayScale() - 1.0f) / 2.0f + 1.0f

        if (gameAR <= deviceAR) {
            mAdjustX = gameAR / deviceAR
            mAdjustY = 1.0f
            mGameWidthHalf = Math.round(mMaxHeight * gameAR) / 2.0f
            mGameHeightHalf = mMaxHeight / 2.0f
        } else {
            mAdjustX = 1.0f
            mAdjustY = gameAR / deviceAR
            mGameWidthHalf = mMaxWidth / 2.0f
            mGameHeightHalf = Math.round(mMaxWidth / gameAR) / 2.0f
        }
    }

    fun setType(type: Int) {
        reset()
        mType = type

        if (type == TYPE_CLICK) {
            // click
            mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_IR + 1
            mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2
            mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_IR + 3
            mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4
        } else if (type == TYPE_STICK) {
            // stick
            mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_IR + 1
            mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2
            mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_IR + 3
            mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4
        }
    }

    fun reset() {
        pointerId = -1
        for (i in 0..3) {
            mAxises[i] = 0.0f
            NativeLibrary.onGamePadMoveEvent(
                NativeLibrary.TouchScreenDevice,
                NativeLibrary.ButtonType.WIIMOTE_IR + i + 1, 0.0f
            )
        }
    }

    fun onPointerDown(id: Int, x: Float, y: Float) {
        pointerId = id
        mCenterX = x
        mCenterY = y
        setPointerState(x, y)

        if (mType == TYPE_CLICK) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - mLastClickTime < 300) {
                mIsDoubleClick = true
                NativeLibrary.onGamePadEvent(
                    NativeLibrary.TouchScreenDevice,
                    mClickButtonId,
                    NativeLibrary.ButtonState.PRESSED
                )
            }
            mLastClickTime = currentTime
        }
    }

    fun onPointerMove(id: Int, x: Float, y: Float) {
        setPointerState(x, y)
    }

    fun onPointerUp(id: Int, x: Float, y: Float) {
        pointerId = -1
        setPointerState(x, y)

        if (mIsDoubleClick) {
            NativeLibrary.onGamePadEvent(
                NativeLibrary.TouchScreenDevice,
                mClickButtonId,
                NativeLibrary.ButtonState.RELEASED
            )
            mIsDoubleClick = false
        }
    }

    private fun setPointerState(x: Float, y: Float) {
        val axises = FloatArray(4)
        val scale = mDisplayScale

        if (mType == TYPE_CLICK) {
            // click
            axises[1] = ((y * mAdjustY) - mGameHeightHalf) / mGameHeightHalf / scale
            axises[0] = axises[1]
            axises[3] = ((x * mAdjustX) - mGameWidthHalf) / mGameWidthHalf / scale
            axises[2] = axises[3]
        } else if (mType == TYPE_STICK) {
            // stick
            axises[1] = (y - mCenterY) / mGameHeightHalf * mScaledDensity / scale / 2.0f
            axises[0] = axises[1]
            axises[3] = (x - mCenterX) / mGameWidthHalf * mScaledDensity / scale / 2.0f
            axises[2] = axises[3]
        }

        for (i in mAxisIDs.indices) {
            var value = mAxises[i] + axises[i]
            if (pointerId == -1) {
                if (InputOverlay.sIRRecenter) {
                    // recenter
                    value = 0f
                }
                if (mType == TYPE_STICK) {
                    // stick, save current value
                    mAxises[i] = value
                }
            }
            NativeLibrary.onGamePadMoveEvent(
                NativeLibrary.TouchScreenDevice,
                mAxisIDs[i], value
            )
        }
    }

    companion object {
        const val TYPE_OFF: Int = 0
        const val TYPE_CLICK: Int = 1
        const val TYPE_STICK: Int = 2
    }
}
