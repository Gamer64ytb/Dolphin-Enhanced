package org.dolphinemu.dolphinemu.features.settings.model.view

import org.dolphinemu.dolphinemu.features.settings.model.Setting
import org.dolphinemu.dolphinemu.features.settings.model.StringSetting

class StringSingleChoiceSetting(
    key: String?,
    section: String?,
    titleId: Int,
    descriptionId: Int,
    val choicesId: Array<String>,
    val valuesId: Array<String>?,
    private val defaultValue: String,
    setting: Setting?
) :
    SettingsItem(key, section, setting, titleId, descriptionId) {
    fun getValueAt(index: Int): String? {
        if (valuesId == null) return null

        if (index >= 0 && index < valuesId.size) {
            return valuesId[index]
        }

        return ""
    }

    val selectedValue: String?
        get() {
            if (setting != null) {
                val setting = setting as StringSetting
                return setting.value
            } else {
                return defaultValue
            }
        }

    val selectValueIndex: Int
        get() {
            val selectedValue = selectedValue
            for (i in valuesId!!.indices) {
                if (valuesId[i] == selectedValue) {
                    return i
                }
            }

            return -1
        }

    /**
     * Write a value to the backing int. If that int was previously null,
     * initializes a new one and returns it, so it can be added to the Hashmap.
     *
     * @param selection New value of the int.
     * @return null if overwritten successfully otherwise; a newly created IntSetting.
     */
    fun setSelectedValue(selection: String?): StringSetting? {
        if (setting == null) {
            val setting = StringSetting(key, section, selection)
            setSetting(setting)
            return setting
        } else {
            val setting = setting as StringSetting
            setting.value = selection
            return null
        }
    }

    override fun getType(): Int {
        return TYPE_STRING_SINGLE_CHOICE
    }
}


