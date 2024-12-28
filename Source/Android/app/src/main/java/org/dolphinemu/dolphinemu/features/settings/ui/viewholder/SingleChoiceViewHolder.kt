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
    private var mItem: SettingsItem? = null

    private var mTextSettingName: TextView? = null
    private var mTextSettingDescription: TextView? = null

    override fun findViews(root: View) {
        mTextSettingName = root.findViewById(R.id.text_setting_name)
        mTextSettingDescription = root.findViewById(R.id.text_setting_description)
    }

    override fun bind(item: SettingsItem) {
        mItem = item
        mTextSettingName!!.setText(item.nameId)

        if (item.descriptionId > 0) {
            mTextSettingDescription!!.setText(item.descriptionId)
        } else if (item is SingleChoiceSetting) {
            val setting = item
            val selected = setting.selectedValue
            val resMgr = mTextSettingDescription!!.context.resources
            val choices = resMgr.getStringArray(setting.choicesId)
            val values = resMgr.getIntArray(setting.valuesId)
            for (i in values.indices) {
                if (values[i] == selected) {
                    mTextSettingDescription!!.text = choices[i]
                }
            }
        } else if (item is StringSingleChoiceSetting) {
            val setting = item
            val choices = setting.choicesId
            val valueIndex = setting.selectValueIndex
            if (valueIndex != -1) mTextSettingDescription!!.text = choices[valueIndex]
        }
    }

    override fun onClick(clicked: View) {
        val position = adapterPosition
        if (mItem is SingleChoiceSetting) {
            adapter.onSingleChoiceClick((mItem as SingleChoiceSetting?)!!, position)
        } else if (mItem is StringSingleChoiceSetting) {
            adapter.onStringSingleChoiceClick((mItem as StringSingleChoiceSetting?)!!, position)
        }
    }
}
