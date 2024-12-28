package org.dolphinemu.dolphinemu.features.settings.ui.viewholder

import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.view.CheckBoxSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsAdapter

class CheckBoxSettingViewHolder(itemView: View, adapter: SettingsAdapter) :
    SettingViewHolder(itemView, adapter) {
    private var item: CheckBoxSetting? = null

    private var textSettingName: TextView? = null
    private var textSettingDescription: TextView? = null

    private var checkbox: CheckBox? = null

    override fun findViews(root: View) {
        textSettingName = root.findViewById(R.id.text_setting_name)
        textSettingDescription = root.findViewById(R.id.text_setting_description)
        checkbox = root.findViewById(R.id.checkbox)
    }

    override fun bind(item: SettingsItem) {
        this.item = item as CheckBoxSetting
        textSettingName!!.setText(item.getNameId())
        if (item.getDescriptionId() > 0) {
            textSettingDescription!!.setText(item.getDescriptionId())
        }
        checkbox!!.isChecked = this.item!!.isChecked
    }

    override fun onClick(clicked: View) {
        checkbox!!.toggle()
        adapter.onBooleanClick(item!!, adapterPosition, checkbox!!.isChecked)
    }
}
