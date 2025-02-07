package org.dolphinemu.dolphinemu.features.settings.ui.viewholder

import android.view.View
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.view.CheckBoxSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsAdapter

class SwitchSettingViewHolder(itemView: View, adapter: SettingsAdapter) :
    SettingViewHolder(itemView, adapter) {
    private var item: CheckBoxSetting? = null

    private var textSettingName: TextView? = null
    private var textSettingDescription: TextView? = null
    private var switchWidget: MaterialSwitch? = null

    override fun findViews(root: View) {
        textSettingName = root.findViewById(R.id.text_setting_name)
        textSettingDescription = root.findViewById(R.id.text_setting_description)
        switchWidget = root.findViewById(R.id.switch_widget)
    }

    override fun bind(item: SettingsItem) {
        this.item = item as CheckBoxSetting
        textSettingName!!.setText(item.getNameId())
        if (item.getDescriptionId() > 0) {
            textSettingDescription!!.setText(item.getDescriptionId())
        }
        switchWidget!!.isChecked = this.item!!.isChecked
    }

    override fun onClick(clicked: View) {
        switchWidget!!.toggle()
        adapter.onBooleanClick(item!!, adapterPosition, switchWidget!!.isChecked)
    }
}
