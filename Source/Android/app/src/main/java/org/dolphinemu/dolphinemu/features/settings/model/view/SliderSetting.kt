package org.dolphinemu.dolphinemu.features.settings.model.view

import org.dolphinemu.dolphinemu.features.settings.model.FloatSetting
import org.dolphinemu.dolphinemu.features.settings.model.IntSetting
import org.dolphinemu.dolphinemu.features.settings.model.Setting
import org.dolphinemu.dolphinemu.utils.Log

class SliderSetting(
    key: String?, section: String?, titleId: Int, descriptionId: Int, val max: Int,
    val units: String, private val defaultValue: Int, setting: Setting?
) : SettingsItem(key, section, setting, titleId, descriptionId) {
    val selectedValue: Int
        get() {
            val setting = setting ?: return defaultValue

            if (setting is IntSetting) {
                return setting.value
            } else if (setting is FloatSetting) {
                val floatSetting = setting
                return if (isPercentSetting) {
                    Math.round(floatSetting.value * 100)
                } else {
                    Math.round(floatSetting.value)
                }
            } else {
                Log.error("[SliderSetting] Error casting setting type.")
                return -1
            }
        }

    private val isPercentSetting: Boolean
        get() = "%" == units

    fun setSelectedValue(selection: Int): Setting? {
        if (setting == null) {
            if (isPercentSetting) {
                val setting = FloatSetting(key, section, selection / 100.0f)
                setSetting(setting)
                return setting
            } else {
                val setting = IntSetting(key, section, selection)
                setSetting(setting)
                return setting
            }
        } else if (setting is FloatSetting) {
            val setting = setting as FloatSetting
            if (isPercentSetting) setting.value = selection / 100.0f
            else setting.value = selection.toFloat()
            return null
        } else {
            val setting = setting as IntSetting
            setting.value = selection
            return null
        }
    }

    override fun getType(): Int {
        return TYPE_SLIDER
    }
}
