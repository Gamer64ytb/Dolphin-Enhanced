package org.dolphinemu.dolphinemu.features.settings.model.view

import android.view.InputDevice
import android.view.InputDevice.MotionRange
import android.view.KeyEvent
import org.dolphinemu.dolphinemu.features.settings.model.Setting
import org.dolphinemu.dolphinemu.features.settings.model.StringSetting

open class InputBindingSetting(key: String?, section: String?, titleId: Int, setting: Setting?) :
    SettingsItem(key, section, setting, titleId, 0) {
    open var value: String?
        get() {
            if (setting == null) {
                return ""
            }

            val setting = setting as StringSetting
            return setting.value
        }
        /**
         * Write a value to the backing string. If that string was previously null,
         * initializes a new one and returns it, so it can be added to the Hashmap.
         *
         * @param bind The input that will be bound
         */
        set(bind) {
            if (setting == null) {
                val setting = StringSetting(key, section, bind)
                setSetting(setting)
            } else {
                val setting = setting as StringSetting
                setting.value = bind
            }
        }

    /**
     * Saves the provided key input setting both to the INI file (so native code can use it) and as
     * an Android preference (so it persists correctly and is human-readable.)
     *
     * @param keyEvent KeyEvent of this key press.
     */
    open fun onKeyInput(keyEvent: KeyEvent) {
        val device = keyEvent.device
        val bindStr = "Device '" + device.descriptor + "'-Button " + keyEvent.keyCode
        value = bindStr
    }

    /**
     * Saves the provided motion input setting both to the INI file (so native code can use it) and as
     * an Android preference (so it persists correctly and is human-readable.)
     *
     * @param device      InputDevice from which the input event originated.
     * @param motionRange MotionRange of the movement
     * @param axisDir     Either '-' or '+'
     */
    open fun onMotionInput(device: InputDevice, motionRange: MotionRange, axisDir: Char) {
        val bindStr =
            "Device '" + device.descriptor + "'-Axis " + motionRange.axis + axisDir
        value = bindStr
    }

    fun clearValue() {
        value = ""
    }

    override fun getType(): Int {
        return TYPE_INPUT_BINDING
    }

    val settingText: String?
        get() {
            var uiText: String? = null
            if (setting != null) {
                val bindStr = setting.valueAsString
                val index = bindStr.indexOf('-')
                uiText = if (index > 0) {
                    bindStr.substring(index + 1)
                } else {
                    bindStr
                }
            }
            return uiText
        }
}
