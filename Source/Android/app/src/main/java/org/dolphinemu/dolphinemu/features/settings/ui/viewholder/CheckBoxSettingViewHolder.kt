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
    private var mItem: CheckBoxSetting? = null

    private var mTextSettingName: TextView? = null
    private var mTextSettingDescription: TextView? = null

    private var mCheckbox: CheckBox? = null

    override fun findViews(root: View) {
        mTextSettingName = root.findViewById(R.id.text_setting_name)
        mTextSettingDescription = root.findViewById(R.id.text_setting_description)
        mCheckbox = root.findViewById(R.id.checkbox)
    }

    override fun bind(item: SettingsItem) {
        mItem = item as CheckBoxSetting
        mTextSettingName!!.setText(item.getNameId())
        if (item.getDescriptionId() > 0) {
            mTextSettingDescription!!.setText(item.getDescriptionId())
        }
        mCheckbox!!.isChecked = mItem!!.isChecked
    }

    override fun onClick(clicked: View) {
        mCheckbox!!.toggle()
        adapter.onBooleanClick(mItem!!, adapterPosition, mCheckbox!!.isChecked)
    }
}
