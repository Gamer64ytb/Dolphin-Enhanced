package org.dolphinemu.dolphinemu.features.settings.ui.viewholder

import android.view.View
import android.widget.TextView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.view.RumbleBindingSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsAdapter

class RumbleBindingViewHolder(itemView: View, adapter: SettingsAdapter) :
    SettingViewHolder(itemView, adapter) {
    private var item: RumbleBindingSetting? = null

    private var textSettingName: TextView? = null
    private var textSettingDescription: TextView? = null

    override fun findViews(root: View) {
        textSettingName = root.findViewById(R.id.text_setting_name)
        textSettingDescription = root.findViewById(R.id.text_setting_description)
    }

    override fun bind(item: SettingsItem) {
        this.item = item as RumbleBindingSetting
        textSettingName!!.setText(item.getNameId())
        textSettingDescription!!.text = this.item!!.settingText
    }

    override fun onClick(clicked: View) {
        adapter.onInputBindingClick(item!!, adapterPosition)
    }
}
