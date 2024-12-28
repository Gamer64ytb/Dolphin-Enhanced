package org.dolphinemu.dolphinemu.overlay

import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.activities.EmulationActivity.Companion.get
import kotlin.math.abs

class InputOverlaySensor {
    private val mAxisIDs = intArrayOf(0, 0, 0, 0)
    private val mFactors = floatArrayOf(1f, 1f, 1f, 1f)
    private var mAccuracyChanged = false
    private var mBaseYaw = 0f
    private var mBasePitch = 0f
    private var mBaseRoll = 0f

    fun setAxisIDs() {
        if (get()!!.isGameCubeGame) {
            when (InputOverlay.sSensorGCSetting) {
                InputOverlay.SENSOR_GC_JOYSTICK -> {
                    mAxisIDs[0] = NativeLibrary.ButtonType.STICK_MAIN + 1
                    mAxisIDs[1] = NativeLibrary.ButtonType.STICK_MAIN + 2
                    mAxisIDs[2] = NativeLibrary.ButtonType.STICK_MAIN + 3
                    mAxisIDs[3] = NativeLibrary.ButtonType.STICK_MAIN + 4
                }

                InputOverlay.SENSOR_GC_CSTICK -> {
                    mAxisIDs[0] = NativeLibrary.ButtonType.STICK_C + 1
                    mAxisIDs[1] = NativeLibrary.ButtonType.STICK_C + 2
                    mAxisIDs[2] = NativeLibrary.ButtonType.STICK_C + 3
                    mAxisIDs[3] = NativeLibrary.ButtonType.STICK_C + 4
                }

                InputOverlay.SENSOR_GC_DPAD -> {
                    mAxisIDs[0] = NativeLibrary.ButtonType.BUTTON_UP
                    mAxisIDs[1] = NativeLibrary.ButtonType.BUTTON_DOWN
                    mAxisIDs[2] = NativeLibrary.ButtonType.BUTTON_LEFT
                    mAxisIDs[3] = NativeLibrary.ButtonType.BUTTON_RIGHT
                }
            }
        } else {
            when (InputOverlay.sSensorWiiSetting) {
                InputOverlay.SENSOR_WII_DPAD -> {
                    mFactors[0] = 1f
                    mFactors[1] = 1f
                    mFactors[2] = 1f
                    mFactors[3] = 1f

                    mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_UP
                    mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_DOWN
                    mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_LEFT
                    mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_RIGHT
                }

                InputOverlay.SENSOR_WII_STICK -> {
                    mFactors[0] = 1f
                    mFactors[1] = 1f
                    mFactors[2] = 1f
                    mFactors[3] = 1f
                    if (InputOverlay.sControllerType == InputOverlay.CONTROLLER_CLASSIC) {
                        mAxisIDs[0] = NativeLibrary.ButtonType.CLASSIC_STICK_LEFT_UP
                        mAxisIDs[1] = NativeLibrary.ButtonType.CLASSIC_STICK_LEFT_DOWN
                        mAxisIDs[2] = NativeLibrary.ButtonType.CLASSIC_STICK_LEFT_LEFT
                        mAxisIDs[3] = NativeLibrary.ButtonType.CLASSIC_STICK_LEFT_RIGHT
                    } else {
                        mAxisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_STICK + 1
                        mAxisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_STICK + 2
                        mAxisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_STICK + 3
                        mAxisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_STICK + 4
                    }
                }

                InputOverlay.SENSOR_WII_IR -> {
                    mFactors[0] = -1f
                    mFactors[1] = -1f
                    mFactors[2] = -1f
                    mFactors[3] = -1f

                    mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_IR + 1
                    mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2
                    mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_IR + 3
                    mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4
                }

                InputOverlay.SENSOR_WII_SWING -> {
                    mFactors[0] = 1f
                    mFactors[1] = 1f
                    mFactors[2] = 1f
                    mFactors[3] = 1f

                    mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_SWING + 1
                    mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SWING + 2
                    mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SWING + 3
                    mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SWING + 4
                }

                InputOverlay.SENSOR_WII_TILT -> if (InputOverlay.sControllerType == InputOverlay.CONTROLLER_WIINUNCHUK) {
                    mFactors[0] = 0.5f
                    mFactors[1] = 0.5f
                    mFactors[2] = 0.5f
                    mFactors[3] = 0.5f

                    mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_TILT + 1 // up
                    mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_TILT + 2 // down
                    mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_TILT + 3 // left
                    mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_TILT + 4 // right
                } else {
                    mFactors[0] = -0.5f
                    mFactors[1] = -0.5f
                    mFactors[2] = 0.5f
                    mFactors[3] = 0.5f

                    mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_TILT + 4 // right
                    mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_TILT + 3 // left
                    mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_TILT + 1 // up
                    mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_TILT + 2 // down
                }

                InputOverlay.SENSOR_WII_SHAKE -> {
                    mAxisIDs[0] = 0
                    mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_X
                    mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Y
                    mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Z
                }

                InputOverlay.SENSOR_NUNCHUK_SWING -> {
                    mFactors[0] = 1f
                    mFactors[1] = 1f
                    mFactors[2] = 1f
                    mFactors[3] = 1f

                    mAxisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_SWING + 1
                    mAxisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_SWING + 2
                    mAxisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_SWING + 3
                    mAxisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_SWING + 4
                }

                InputOverlay.SENSOR_NUNCHUK_TILT -> {
                    mFactors[0] = 0.5f
                    mFactors[1] = 0.5f
                    mFactors[2] = 0.5f
                    mFactors[3] = 0.5f

                    mAxisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_TILT + 1 // up
                    mAxisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_TILT + 2 // down
                    mAxisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_TILT + 3 // left
                    mAxisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_TILT + 4 // right
                }

                InputOverlay.SENSOR_NUNCHUK_SHAKE -> {
                    mAxisIDs[0] = 0
                    mAxisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_X
                    mAxisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_Y
                    mAxisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_Z
                }
            }
        }
    }

    fun onSensorChanged(rotation: FloatArray) {
        // portrait:  yaw(0) - pitch(1) - roll(2)
        // landscape: yaw(0) - pitch(2) - roll(1)
        if (mAccuracyChanged) {
            if (abs((mBaseYaw - rotation[0]).toDouble()) > 0.1f || abs((mBasePitch - rotation[2]).toDouble()) > 0.1f || abs(
                    (mBaseRoll - rotation[1]).toDouble()
                ) > 0.1f
            ) {
                mBaseYaw = rotation[0]
                mBasePitch = rotation[2]
                mBaseRoll = rotation[1]
                return
            }
            mAccuracyChanged = false
        }

        var z = mBaseYaw - rotation[0]
        var y = mBasePitch - rotation[2]
        var x = mBaseRoll - rotation[1]
        val axises = FloatArray(4)

        z = (z * (1 + abs(z.toDouble()))).toFloat()
        y = (y * (1 + abs(y.toDouble()))).toFloat()
        x = (x * (1 + abs(x.toDouble()))).toFloat()

        axises[0] = y // up
        axises[1] = y // down
        axises[2] = x // left
        axises[3] = x // right

        if (!get()!!.isGameCubeGame &&
            (InputOverlay.SENSOR_WII_SHAKE == InputOverlay.sSensorWiiSetting ||
                    InputOverlay.SENSOR_NUNCHUK_SHAKE == InputOverlay.sSensorWiiSetting)
        ) {
            axises[0] = 0f
            axises[1] = x
            axises[2] = -y
            axises[3] = y
            handleShakeEvent(axises)
            return
        }

        for (i in mAxisIDs.indices) {
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
                        NativeLibrary.TouchScreenDevice,
                        mAxisIDs[i],
                        NativeLibrary.ButtonState.PRESSED
                    )
                }
            } else if (InputOverlay.sShakeStates[i] != NativeLibrary.ButtonState.RELEASED) {
                InputOverlay.sShakeStates[i] = NativeLibrary.ButtonState.RELEASED
                NativeLibrary.onGamePadEvent(
                    NativeLibrary.TouchScreenDevice,
                    mAxisIDs[i],
                    NativeLibrary.ButtonState.RELEASED
                )
            }
        }
    }

    fun onAccuracyChanged(accuracy: Int) {
        // init value
        mBaseYaw = Math.PI.toFloat()
        mBasePitch = Math.PI.toFloat()
        mBaseRoll = Math.PI.toFloat()

        // reset current state
        val rotation = floatArrayOf(mBaseYaw, mBasePitch, mBaseRoll)
        onSensorChanged(rotation)

        // begin new state
        setAxisIDs()
        mAccuracyChanged = true
    }
}
