package org.dolphinemu.dolphinemu.features.settings.ui.viewholder

import android.view.View
import android.widget.TextView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.view.InputBindingSetting
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsAdapter

class InputBindingSettingViewHolder(itemView: View, adapter: SettingsAdapter) :
    SettingViewHolder(itemView, adapter) {
    private var mItem: InputBindingSetting? = null

    private var mTextSettingName: TextView? = null
    private var mTextSettingDescription: TextView? = null

    override fun findViews(root: View) {
        mTextSettingName = root.findViewById(R.id.text_setting_name)
        mTextSettingDescription = root.findViewById(R.id.text_setting_description)
    }

    override fun bind(item: SettingsItem) {
        mItem = item as InputBindingSetting
        mTextSettingName!!.setText(mItem!!.nameId)
        mTextSettingDescription!!.text = mItem!!.settingText
    }

    override fun onClick(clicked: View) {
        adapter.onInputBindingClick(mItem!!, adapterPosition)
    }
}
