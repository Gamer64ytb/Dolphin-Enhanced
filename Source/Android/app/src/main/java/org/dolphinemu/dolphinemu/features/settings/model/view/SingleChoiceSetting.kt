package org.dolphinemu.dolphinemu.features.settings.model.view

import org.dolphinemu.dolphinemu.features.settings.model.IntSetting
import org.dolphinemu.dolphinemu.features.settings.model.Setting
import org.dolphinemu.dolphinemu.features.settings.ui.MenuTag

class SingleChoiceSetting @JvmOverloads constructor(
    key: String?,
    section: String?,
    titleId: Int,
    descriptionId: Int,
    val choicesId: Int,
    val valuesId: Int,
    private val defaultValue: Int,
    setting: Setting?,
    val menuTag: MenuTag? = null
) :
    SettingsItem(key, section, setting, titleId, descriptionId) {
    val selectedValue: Int
        get() {
            if (setting != null) {
                val setting = setting as IntSetting
                return setting.value
            } else {
                return defaultValue
            }
        }

    /**
     * Write a value to the backing int. If that int was previously null,
     * initializes a new one and returns it, so it can be added to the Hashmap.
     *
     * @param selection New value of the int.
     * @return null if overwritten successfully otherwise; a newly created IntSetting.
     */
    fun setSelectedValue(selection: Int): IntSetting? {
        if (setting == null) {
            val setting = IntSetting(key, section, selection)
            setSetting(setting)
            return setting
        } else {
            val setting = setting as IntSetting
            setting.value = selection
            return null
        }
    }

    override fun getType(): Int {
        return TYPE_SINGLE_CHOICE
    }
}
