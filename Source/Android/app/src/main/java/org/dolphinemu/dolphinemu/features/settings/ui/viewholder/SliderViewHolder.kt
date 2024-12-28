package org.dolphinemu.dolphinemu.features.settings.ui.viewholder

import android.view.View
import android.widget.TextView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem
import org.dolphinemu.dolphinemu.features.settings.model.view.SliderSetting
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsAdapter

class SliderViewHolder(itemView: View, adapter: SettingsAdapter) :
    SettingViewHolder(itemView, adapter) {
    private var item: SliderSetting? = null

    private var textSettingName: TextView? = null
    private var textSettingDescription: TextView? = null

    override fun findViews(root: View) {
        textSettingName = root.findViewById(R.id.text_setting_name)
        textSettingDescription = root.findViewById(R.id.text_setting_description)
    }

    override fun bind(item: SettingsItem) {
        this.item = item as SliderSetting
        textSettingName!!.setText(item.getNameId())
        if (item.getDescriptionId() > 0) {
            textSettingDescription!!.setText(item.getDescriptionId())
        } else {
            textSettingDescription!!.text = this.item!!.selectedValue.toString() + this.item!!.units
        }
    }

    override fun onClick(clicked: View) {
        adapter.onSliderClick(item!!, adapterPosition)
    }
}

