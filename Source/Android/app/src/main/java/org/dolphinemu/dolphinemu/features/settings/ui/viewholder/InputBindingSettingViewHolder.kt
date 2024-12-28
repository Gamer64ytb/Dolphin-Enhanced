package org.dolphinemu.dolphinemu.features.settings.ui.viewholder

import android.view.View
import android.widget.TextView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.view.InputBindingSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsAdapter

class InputBindingSettingViewHolder(itemView: View, adapter: SettingsAdapter) :
    SettingViewHolder(itemView, adapter) {
    private var item: InputBindingSetting? = null

    private var textSettingName: TextView? = null
    private var textSettingDescription: TextView? = null

    override fun findViews(root: View) {
        textSettingName = root.findViewById(R.id.text_setting_name)
        textSettingDescription = root.findViewById(R.id.text_setting_description)
    }

    override fun bind(item: SettingsItem) {
        this.item = item as InputBindingSetting
        textSettingName!!.setText(this.item!!.nameId)
        textSettingDescription!!.text = this.item!!.settingText
    }

    override fun onClick(clicked: View) {
        adapter.onInputBindingClick(item!!, adapterPosition)
    }
}
