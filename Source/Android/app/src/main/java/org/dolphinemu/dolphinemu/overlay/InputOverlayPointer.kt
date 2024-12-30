package org.dolphinemu.dolphinemu.overlay

import org.dolphinemu.dolphinemu.NativeLibrary

class InputOverlayPointer
    (width: Int, height: Int, scaledDensity: Float) {
    private var mType: Int
    var pointerId: Int
        private set

    private var displayScale: Float
    private val scaledDensity: Float
    private val maxWidth: Int
    private val maxHeight: Int
    private var gameWidthHalf: Float
    private var gameHeightHalf: Float
    private var adjustX: Float
    private var adjustY: Float

    private val axisIDs = IntArray(4)

    // used for stick
    private val axises = FloatArray(4)
    private var centerX = 0f
    private var centerY = 0f

    // used for click
    private var lastClickTime: Long
    private val clickButtonId: Int
    private var isDoubleClick: Boolean

    init {
        mType = TYPE_OFF
        axisIDs[0] = 0
        axisIDs[1] = 0
        axisIDs[2] = 0
        axisIDs[3] = 0

        displayScale = 1.0f
        this.scaledDensity = scaledDensity
        maxWidth = width
        maxHeight = height
        gameWidthHalf = width / 2.0f
        gameHeightHalf = height / 2.0f
        adjustX = 1.0f
        adjustY = 1.0f

        pointerId = -1

        lastClickTime = 0
        isDoubleClick = false
        clickButtonId = NativeLibrary.ButtonType.WIIMOTE_BUTTON_A

        if (NativeLibrary.IsRunning()) {
            updateTouchPointer()
        }
    }

    fun updateTouchPointer() {
        val deviceAR = maxWidth.toFloat() / maxHeight.toFloat()
        val gameAR = NativeLibrary.GetGameAspectRatio()
        // same scale ratio in renderbase.cpp
        displayScale = (NativeLibrary.GetGameDisplayScale() - 1.0f) / 2.0f + 1.0f

        if (gameAR <= deviceAR) {
            adjustX = gameAR / deviceAR
            adjustY = 1.0f
            gameWidthHalf = Math.round(maxHeight * gameAR) / 2.0f
            gameHeightHalf = maxHeight / 2.0f
        } else {
            adjustX = 1.0f
            adjustY = gameAR / deviceAR
            gameWidthHalf = maxWidth / 2.0f
            gameHeightHalf = Math.round(maxWidth / gameAR) / 2.0f
        }
    }

    fun setType(type: Int) {
        reset()
        mType = type

        if (type == TYPE_CLICK) {
            // click
            axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_IR + 1
            axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2
            axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_IR + 3
            axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4
        } else if (type == TYPE_STICK) {
            // stick
            axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_IR + 1
            axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2
            axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_IR + 3
            axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4
        }
    }

    fun reset() {
        pointerId = -1
        for (i in 0..3) {
            axises[i] = 0.0f
            NativeLibrary.onGamePadMoveEvent(
                NativeLibrary.TouchScreenDevice,
                NativeLibrary.ButtonType.WIIMOTE_IR + i + 1, 0.0f
            )
        }
    }

    fun onPointerDown(id: Int, x: Float, y: Float) {
        pointerId = id
        centerX = x
        centerY = y
        setPointerState(x, y)

        if (mType == TYPE_CLICK) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 300) {
                isDoubleClick = true
                NativeLibrary.onGamePadEvent(
                    NativeLibrary.TouchScreenDevice,
                    clickButtonId,
                    NativeLibrary.ButtonState.PRESSED
                )
            }
            lastClickTime = currentTime
        }
    }

    fun onPointerMove(id: Int, x: Float, y: Float) {
        setPointerState(x, y)
    }

    fun onPointerUp(id: Int, x: Float, y: Float) {
        pointerId = -1
        setPointerState(x, y)

        if (isDoubleClick) {
            NativeLibrary.onGamePadEvent(
                NativeLibrary.TouchScreenDevice,
                clickButtonId,
                NativeLibrary.ButtonState.RELEASED
            )
            isDoubleClick = false
        }
    }

    private fun setPointerState(x: Float, y: Float) {
        val axises = FloatArray(4)
        val scale = displayScale

        if (mType == TYPE_CLICK) {
            // click
            axises[1] = ((y * adjustY) - gameHeightHalf) / gameHeightHalf / scale
            axises[0] = axises[1]
            axises[3] = ((x * adjustX) - gameWidthHalf) / gameWidthHalf / scale
            axises[2] = axises[3]
        } else if (mType == TYPE_STICK) {
            // stick
            axises[1] = (y - centerY) / gameHeightHalf * scaledDensity / scale / 2.0f
            axises[0] = axises[1]
            axises[3] = (x - centerX) / gameWidthHalf * scaledDensity / scale / 2.0f
            axises[2] = axises[3]
        }

        for (i in axisIDs.indices) {
            var value = this.axises[i] + axises[i]
            if (pointerId == -1) {
                if (InputOverlay.sIRRecenter) {
                    // recenter
                    value = 0f
                }
                if (mType == TYPE_STICK) {
                    // stick, save current value
                    this.axises[i] = value
                }
            }
            NativeLibrary.onGamePadMoveEvent(
                NativeLibrary.TouchScreenDevice,
                axisIDs[i], value
            )
        }
    }

    companion object {
        const val TYPE_OFF = 0
        const val TYPE_CLICK = 1
        const val TYPE_STICK = 2
    }
}
