package org.dolphinemu.dolphinemu.features.settings.ui.viewholder

import android.view.View
import android.widget.TextView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem
import org.dolphinemu.dolphinemu.features.settings.model.view.SingleChoiceSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.StringSingleChoiceSetting
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsAdapter

class SingleChoiceViewHolder(itemView: View, adapter: SettingsAdapter) :
    SettingViewHolder(itemView, adapter) {
    private var item: SettingsItem? = null

    private var textSettingName: TextView? = null
    private var textSettingDescription: TextView? = null

    override fun findViews(root: View) {
        textSettingName = root.findViewById(R.id.text_setting_name)
        textSettingDescription = root.findViewById(R.id.text_setting_description)
    }

    override fun bind(item: SettingsItem) {
        this.item = item
        textSettingName!!.setText(item.nameId)

        if (item.descriptionId > 0) {
            textSettingDescription!!.setText(item.descriptionId)
        } else if (item is SingleChoiceSetting) {
            val setting = item
            val selected = setting.selectedValue
            val resMgr = textSettingDescription!!.context.resources
            val choices = resMgr.getStringArray(setting.choicesId)
            val values = resMgr.getIntArray(setting.valuesId)
            for (i in values.indices) {
                if (values[i] == selected) {
                    textSettingDescription!!.text = choices[i]
                }
            }
        } else if (item is StringSingleChoiceSetting) {
            val setting = item
            val choices = setting.choicesId
            val valueIndex = setting.selectValueIndex
            if (valueIndex != -1) textSettingDescription!!.text = choices[valueIndex]
        }
    }

    override fun onClick(clicked: View) {
        val position = adapterPosition
        if (item is SingleChoiceSetting) {
            adapter.onSingleChoiceClick((item as SingleChoiceSetting?)!!, position)
        } else if (item is StringSingleChoiceSetting) {
            adapter.onStringSingleChoiceClick((item as StringSingleChoiceSetting?)!!, position)
        }
    }
}
