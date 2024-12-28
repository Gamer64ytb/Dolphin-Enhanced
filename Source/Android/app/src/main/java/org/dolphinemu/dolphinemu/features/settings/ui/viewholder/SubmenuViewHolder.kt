package org.dolphinemu.dolphinemu.features.settings.ui.viewholder

import android.view.View
import android.widget.TextView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem
import org.dolphinemu.dolphinemu.features.settings.model.view.SubmenuSetting
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsAdapter

class SubmenuViewHolder(itemView: View, adapter: SettingsAdapter) :
    SettingViewHolder(itemView, adapter) {
    private var mItem: SubmenuSetting? = null

    private var mTextSettingName: TextView? = null
    private var mTextSettingDescription: TextView? = null

    override fun findViews(root: View) {
        mTextSettingName = root.findViewById(R.id.text_setting_name)
        mTextSettingDescription = root.findViewById(R.id.text_setting_description)
    }

    override fun bind(item: SettingsItem) {
        mItem = item as SubmenuSetting

        mTextSettingName!!.setText(item.getNameId())

        if (item.getDescriptionId() > 0) {
            mTextSettingDescription!!.setText(item.getDescriptionId())
        }
    }

    override fun onClick(clicked: View) {
        adapter.onSubmenuClick(mItem!!)
    }
}
