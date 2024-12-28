package org.dolphinemu.dolphinemu.overlay

import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.activities.EmulationActivity.Companion.get
import kotlin.math.abs

class InputOverlaySensor {
    private val axisIDs = intArrayOf(0, 0, 0, 0)
    private val factors = floatArrayOf(1f, 1f, 1f, 1f)
    private var accuracyChanged = false
    private var baseYaw = 0f
    private var basePitch = 0f
    private var baseRoll = 0f

    private fun setAxisIDs() {
        if (get()!!.isGameCubeGame) {
            when (InputOverlay.sSensorGCSetting) {
                InputOverlay.SENSOR_GC_JOYSTICK -> {
                    axisIDs[0] = NativeLibrary.ButtonType.STICK_MAIN + 1
                    axisIDs[1] = NativeLibrary.ButtonType.STICK_MAIN + 2
                    axisIDs[2] = NativeLibrary.ButtonType.STICK_MAIN + 3
                    axisIDs[3] = NativeLibrary.ButtonType.STICK_MAIN + 4
                }

                InputOverlay.SENSOR_GC_CSTICK -> {
                    axisIDs[0] = NativeLibrary.ButtonType.STICK_C + 1
                    axisIDs[1] = NativeLibrary.ButtonType.STICK_C + 2
                    axisIDs[2] = NativeLibrary.ButtonType.STICK_C + 3
                    axisIDs[3] = NativeLibrary.ButtonType.STICK_C + 4
                }

                InputOverlay.SENSOR_GC_DPAD -> {
                    axisIDs[0] = NativeLibrary.ButtonType.BUTTON_UP
                    axisIDs[1] = NativeLibrary.ButtonType.BUTTON_DOWN
                    axisIDs[2] = NativeLibrary.ButtonType.BUTTON_LEFT
                    axisIDs[3] = NativeLibrary.ButtonType.BUTTON_RIGHT
                }
            }
        } else {
            when (InputOverlay.sSensorWiiSetting) {
                InputOverlay.SENSOR_WII_DPAD -> {
                    factors[0] = 1f
                    factors[1] = 1f
                    factors[2] = 1f
                    factors[3] = 1f

                    axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_UP
                    axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_DOWN
                    axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_LEFT
                    axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_RIGHT
                }

                InputOverlay.SENSOR_WII_STICK -> {
                    factors[0] = 1f
                    factors[1] = 1f
                    factors[2] = 1f
                    factors[3] = 1f
                    if (InputOverlay.sControllerType == InputOverlay.CONTROLLER_CLASSIC) {
                        axisIDs[0] = NativeLibrary.ButtonType.CLASSIC_STICK_LEFT_UP
                        axisIDs[1] = NativeLibrary.ButtonType.CLASSIC_STICK_LEFT_DOWN
                        axisIDs[2] = NativeLibrary.ButtonType.CLASSIC_STICK_LEFT_LEFT
                        axisIDs[3] = NativeLibrary.ButtonType.CLASSIC_STICK_LEFT_RIGHT
                    } else {
                        axisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_STICK + 1
                        axisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_STICK + 2
                        axisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_STICK + 3
                        axisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_STICK + 4
                    }
                }

                InputOverlay.SENSOR_WII_IR -> {
                    factors[0] = -1f
                    factors[1] = -1f
                    factors[2] = -1f
                    factors[3] = -1f

                    axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_IR + 1
                    axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2
                    axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_IR + 3
                    axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4
                }

                InputOverlay.SENSOR_WII_SWING -> {
                    factors[0] = 1f
                    factors[1] = 1f
                    factors[2] = 1f
                    factors[3] = 1f

                    axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_SWING + 1
                    axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SWING + 2
                    axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SWING + 3
                    axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SWING + 4
                }

                InputOverlay.SENSOR_WII_TILT -> if (InputOverlay.sControllerType == InputOverlay.CONTROLLER_WIINUNCHUK) {
                    factors[0] = 0.5f
                    factors[1] = 0.5f
                    factors[2] = 0.5f
                    factors[3] = 0.5f

                    axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_TILT + 1 // up
                    axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_TILT + 2 // down
                    axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_TILT + 3 // left
                    axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_TILT + 4 // right
                } else {
                    factors[0] = -0.5f
                    factors[1] = -0.5f
                    factors[2] = 0.5f
                    factors[3] = 0.5f

                    axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_TILT + 4 // right
                    axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_TILT + 3 // left
                    axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_TILT + 1 // up
                    axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_TILT + 2 // down
                }

                InputOverlay.SENSOR_WII_SHAKE -> {
                    axisIDs[0] = 0
                    axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_X
                    axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Y
                    axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Z
                }

                InputOverlay.SENSOR_NUNCHUK_SWING -> {
                    factors[0] = 1f
                    factors[1] = 1f
                    factors[2] = 1f
                    factors[3] = 1f

                    axisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_SWING + 1
                    axisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_SWING + 2
                    axisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_SWING + 3
                    axisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_SWING + 4
                }

                InputOverlay.SENSOR_NUNCHUK_TILT -> {
                    factors[0] = 0.5f
                    factors[1] = 0.5f
                    factors[2] = 0.5f
                    factors[3] = 0.5f

                    axisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_TILT + 1 // up
                    axisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_TILT + 2 // down
                    axisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_TILT + 3 // left
                    axisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_TILT + 4 // right
                }

                InputOverlay.SENSOR_NUNCHUK_SHAKE -> {
                    axisIDs[0] = 0
                    axisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_X
                    axisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_Y
                    axisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_Z
                }
            }
        }
    }

    fun onSensorChanged(rotation: FloatArray) {
        // portrait:  yaw(0) - pitch(1) - roll(2)
        // landscape: yaw(0) - pitch(2) - roll(1)
        if (accuracyChanged) {
            if (abs((baseYaw - rotation[0]).toDouble()) > 0.1f || abs((basePitch - rotation[2]).toDouble()) > 0.1f || abs(
                    (baseRoll - rotation[1]).toDouble()
                ) > 0.1f
            ) {
                baseYaw = rotation[0]
                basePitch = rotation[2]
                baseRoll = rotation[1]
                return
            }
            accuracyChanged = false
        }

        var z = baseYaw - rotation[0]
        var y = basePitch - rotation[2]
        var x = baseRoll - rotation[1]
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

        for (i in axisIDs.indices) {
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
                        NativeLibrary.TouchScreenDevice,
                        axisIDs[i],
                        NativeLibrary.ButtonState.PRESSED
                    )
                }
            } else if (InputOverlay.sShakeStates[i] != NativeLibrary.ButtonState.RELEASED) {
                InputOverlay.sShakeStates[i] = NativeLibrary.ButtonState.RELEASED
                NativeLibrary.onGamePadEvent(
                    NativeLibrary.TouchScreenDevice,
                    axisIDs[i],
                    NativeLibrary.ButtonState.RELEASED
                )
            }
        }
    }

    fun onAccuracyChanged(accuracy: Int) {
        // init value
        baseYaw = Math.PI.toFloat()
        basePitch = Math.PI.toFloat()
        baseRoll = Math.PI.toFloat()

        // reset current state
        val rotation = floatArrayOf(baseYaw, basePitch, baseRoll)
        onSensorChanged(rotation)

        // begin new state
        setAxisIDs()
        accuracyChanged = true
    }
}
