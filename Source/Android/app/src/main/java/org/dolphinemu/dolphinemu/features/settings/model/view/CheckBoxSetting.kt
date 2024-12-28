package org.dolphinemu.dolphinemu.features.settings.model.view

import org.dolphinemu.dolphinemu.features.settings.model.BooleanSetting
import org.dolphinemu.dolphinemu.features.settings.model.Setting
import org.dolphinemu.dolphinemu.features.settings.utils.SettingsFile

class CheckBoxSetting(
    key: String?, section: String?, titleId: Int, descriptionId: Int,
    private val defaultValue: Boolean, setting: Setting?
) : SettingsItem(key, section, setting, titleId, descriptionId) {
    val isChecked: Boolean
        get() {
            var value = defaultValue
            if (setting != null) {
                val setting = setting as BooleanSetting
                value = isInvertedSetting != setting.value
            }
            return value
        }

    private val isInvertedSetting: Boolean
        get() = key == SettingsFile.KEY_SKIP_EFB
                || key == SettingsFile.KEY_IGNORE_FORMAT

    fun setChecked(checked: Boolean): BooleanSetting? {
        var checked = checked
        if (isInvertedSetting) checked = !checked

        if (setting == null) {
            val setting = BooleanSetting(key, section, checked)
            setSetting(setting)
            return setting
        } else {
            val setting = setting as BooleanSetting
            setting.value = checked
            return null
        }
    }

    override fun getType(): Int {
        return TYPE_CHECKBOX
    }
}
