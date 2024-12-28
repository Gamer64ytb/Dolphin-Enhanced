package org.dolphinemu.dolphinemu.features.settings.ui.viewholder

import android.view.View
import android.widget.TextView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem
import org.dolphinemu.dolphinemu.features.settings.model.view.SubmenuSetting
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsAdapter

class SubmenuViewHolder(itemView: View, adapter: SettingsAdapter) :
    SettingViewHolder(itemView, adapter) {
    private var item: SubmenuSetting? = null

    private var textSettingName: TextView? = null
    private var textSettingDescription: TextView? = null

    override fun findViews(root: View) {
        textSettingName = root.findViewById(R.id.text_setting_name)
        textSettingDescription = root.findViewById(R.id.text_setting_description)
    }

    override fun bind(item: SettingsItem) {
        this.item = item as SubmenuSetting

        textSettingName!!.setText(item.getNameId())

        if (item.getDescriptionId() > 0) {
            textSettingDescription!!.setText(item.getDescriptionId())
        }
    }

    override fun onClick(clicked: View) {
        adapter.onSubmenuClick(item!!)
    }
}
