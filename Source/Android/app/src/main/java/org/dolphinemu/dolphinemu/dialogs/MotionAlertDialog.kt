package org.dolphinemu.dolphinemu.dialogs

import android.app.AlertDialog
import android.content.Context
import android.view.InputDevice
import android.view.InputDevice.MotionRange
import android.view.KeyEvent
import android.view.MotionEvent
import org.dolphinemu.dolphinemu.features.settings.model.view.InputBindingSetting
import org.dolphinemu.dolphinemu.utils.ControllerMappingHelper
import org.dolphinemu.dolphinemu.utils.Log
import kotlin.math.abs

/**
 * [AlertDialog] derivative that listens for
 * motion events from controllers and joysticks.
 */
class MotionAlertDialog
/**
 * Constructor
 *
 * @param context The current [Context].
 * @param setting The Preference to show this dialog for.
 */(
    context: Context?, // The selected input preference
    private val setting: InputBindingSetting
) : AlertDialog(context) {
    private val mPreviousValues = ArrayList<Float>()
    private var mPrevDeviceId = 0
    private var mWaitingForEvent = true

    private fun onKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        Log.debug("[MotionAlertDialog] Received key event: " + event.action)
        when (event.action) {
            KeyEvent.ACTION_UP -> {
                if (!ControllerMappingHelper.shouldKeyBeIgnored(event.device, keyCode)) {
                    setting.onKeyInput(event)
                    dismiss()
                }
                // Even if we ignore the key, we still consume it. Thus return true regardless.
                return true
            }

            else -> return false
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Handle this key if we care about it, otherwise pass it down the framework
        return onKeyEvent(event.keyCode, event) || super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        // Handle this event if we care about it, otherwise pass it down the framework
        return onMotionEvent(event) || super.dispatchGenericMotionEvent(event)
    }

    private fun onMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_CLASS_JOYSTICK) == 0) return false
        if (event.action != MotionEvent.ACTION_MOVE) return false

        val input = event.device

        val motionRanges = input.motionRanges

        if (input.id != mPrevDeviceId) {
            mPreviousValues.clear()
        }
        mPrevDeviceId = input.id
        val firstEvent = mPreviousValues.isEmpty()

        var numMovedAxis = 0
        var axisMoveValue = 0.0f
        var lastMovedRange: MotionRange? = null
        var lastMovedDir = '?'
        if (mWaitingForEvent) {
            for (i in motionRanges.indices) {
                val range = motionRanges[i]
                val axis = range.axis
                val origValue = event.getAxisValue(axis)
                val value = ControllerMappingHelper.scaleAxis(input, axis, origValue)
                if (firstEvent) {
                    mPreviousValues.add(value)
                } else {
                    val previousValue = mPreviousValues[i]

                    // Only handle the axes that are not neutral (more than 0.5)
                    // but ignore any axis that has a constant value (e.g. always 1)
                    if (abs(value.toDouble()) > 0.5f && value != previousValue) {
                        // It is common to have multiple axes with the same physical input. For example,
                        // shoulder butters are provided as both AXIS_LTRIGGER and AXIS_BRAKE.
                        // To handle this, we ignore an axis motion that's the exact same as a motion
                        // we already saw. This way, we ignore axes with two names, but catch the case
                        // where a joystick is moved in two directions.
                        // ref: bottom of https://developer.android.com/training/game-controllers/controller-input.html
                        if (value != axisMoveValue) {
                            axisMoveValue = value
                            numMovedAxis++
                            lastMovedRange = range
                            lastMovedDir = if (value < 0.0f) '-' else '+'
                        }
                    } else if (abs(value.toDouble()) < 0.25f && abs(previousValue.toDouble()) > 0.75f) {
                        numMovedAxis++
                        lastMovedRange = range
                        lastMovedDir = if (previousValue < 0.0f) '-' else '+'
                    }
                }

                mPreviousValues[i] = value
            }

            // If only one axis moved, that's the winner.
            if (numMovedAxis == 1) {
                mWaitingForEvent = false
                setting.onMotionInput(input, lastMovedRange, lastMovedDir)
                dismiss()
            }
        }
        return true
    }
}
