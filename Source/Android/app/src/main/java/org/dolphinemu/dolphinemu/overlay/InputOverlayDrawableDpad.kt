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
    private val defaultStateBitmap: BitmapDrawable,
    private val pressedOneDirectionStateBitmap: BitmapDrawable,
    private val pressedTwoDirectionsStateBitmap: BitmapDrawable,
    buttonUp: Int,
    buttonDown: Int,
    buttonLeft: Int,
    buttonRight: Int
) {
    // The ID identifying what type of button this Drawable represents.
    private val buttonIds = IntArray(4)
    private val pressStates = BooleanArray(4)
    var pointerId: Int
        private set
    private var previousTouchX = 0
    private var previousTouchY = 0
    private var controlPositionX = 0
    private var controlPositionY = 0

    init {
        pointerId = -1

        buttonIds[0] = buttonUp
        buttonIds[1] = buttonDown
        buttonIds[2] = buttonLeft
        buttonIds[3] = buttonRight

        pressStates[0] = false
        pressStates[1] = false
        pressStates[2] = false
        pressStates[3] = false
    }

    fun onDraw(canvas: Canvas) {
        val bounds = bounds
        val px = controlPositionX + (bounds.width() / 2)
        val py = controlPositionY + (bounds.height() / 2)

        val up = pressStates[0]
        val down = pressStates[1]
        val left = pressStates[2]
        val right = pressStates[3]

        if (up) {
            if (left) pressedTwoDirectionsStateBitmap.draw(canvas)
            else if (right) {
                canvas.save()
                canvas.rotate(90f, px.toFloat(), py.toFloat())
                pressedTwoDirectionsStateBitmap.draw(canvas)
                canvas.restore()
            } else pressedOneDirectionStateBitmap.draw(canvas)
        } else if (down) {
            if (left) {
                canvas.save()
                canvas.rotate(270f, px.toFloat(), py.toFloat())
                pressedTwoDirectionsStateBitmap.draw(canvas)
                canvas.restore()
            } else if (right) {
                canvas.save()
                canvas.rotate(180f, px.toFloat(), py.toFloat())
                pressedTwoDirectionsStateBitmap.draw(canvas)
                canvas.restore()
            } else {
                canvas.save()
                canvas.rotate(180f, px.toFloat(), py.toFloat())
                pressedOneDirectionStateBitmap.draw(canvas)
                canvas.restore()
            }
        } else if (left) {
            canvas.save()
            canvas.rotate(270f, px.toFloat(), py.toFloat())
            pressedOneDirectionStateBitmap.draw(canvas)
            canvas.restore()
        } else if (right) {
            canvas.save()
            canvas.rotate(90f, px.toFloat(), py.toFloat())
            pressedOneDirectionStateBitmap.draw(canvas)
            canvas.restore()
        } else {
            defaultStateBitmap.draw(canvas)
        }
    }

    fun getButtonId(direction: Int): Int {
        return buttonIds[direction]
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
        controlPositionX = x
        controlPositionY = y
    }

    var bounds: Rect
        get() = defaultStateBitmap.bounds
        set(bounds) {
            defaultStateBitmap.bounds = bounds
            pressedOneDirectionStateBitmap.bounds = bounds
            pressedTwoDirectionsStateBitmap.bounds = bounds
        }

    fun setAlpha(value: Int) {
        defaultStateBitmap.alpha = value
        pressedOneDirectionStateBitmap.alpha = value
        pressedTwoDirectionsStateBitmap.alpha = value
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
            if (pressed[i] != pressStates[i]) {
                NativeLibrary
                    .onGamePadEvent(
                        NativeLibrary.TouchScreenDevice,
                        buttonIds[i],
                        if (pressed[i]) NativeLibrary.ButtonState.PRESSED else NativeLibrary.ButtonState.RELEASED
                    )
            }
        }

        pressStates[0] = pressed[0]
        pressStates[1] = pressed[1]
        pressStates[2] = pressed[2]
        pressStates[3] = pressed[3]
    }
}
