package org.dolphinemu.dolphinemu.features.settings.model.view

import android.view.InputDevice
import android.view.InputDevice.MotionRange
import android.view.KeyEvent
import org.dolphinemu.dolphinemu.features.settings.model.Setting
import org.dolphinemu.dolphinemu.features.settings.model.StringSetting
import org.dolphinemu.dolphinemu.utils.Rumble

class RumbleBindingSetting(key: String?, section: String?, titleId: Int, setting: Setting?) :
    InputBindingSetting(key, section, titleId, setting) {
    override var value: String?
        get() {
            if (setting == null) {
                return ""
            }

            val setting = setting as StringSetting
            return setting.value
        }
        set(value) {
            super.value = value
        }

    /**
     * Just need the device when saving rumble.
     */
    override fun onKeyInput(keyEvent: KeyEvent) {
        saveRumble(keyEvent.device)
    }

    /**
     * Just need the device when saving rumble.
     */
    override fun onMotionInput(device: InputDevice, motionRange: MotionRange, axisDir: Char) {
        saveRumble(device)
    }

    private fun saveRumble(device: InputDevice) {
        val vibrator = device.vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            value = device.descriptor
            Rumble.doRumble(vibrator)
        } else {
            value = ""
        }
    }

    override fun getType(): Int {
        return TYPE_RUMBLE_BINDING
    }
}
