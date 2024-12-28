package org.dolphinemu.dolphinemu.features.settings.ui.viewholder

import android.view.View
import android.widget.TextView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem
import org.dolphinemu.dolphinemu.features.settings.model.view.SliderSetting
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsAdapter

class SliderViewHolder(itemView: View, adapter: SettingsAdapter) :
    SettingViewHolder(itemView, adapter) {
    private var mItem: SliderSetting? = null

    private var mTextSettingName: TextView? = null
    private var mTextSettingDescription: TextView? = null

    override fun findViews(root: View) {
        mTextSettingName = root.findViewById(R.id.text_setting_name)
        mTextSettingDescription = root.findViewById(R.id.text_setting_description)
    }

    override fun bind(item: SettingsItem) {
        mItem = item as SliderSetting
        mTextSettingName!!.setText(item.getNameId())
        if (item.getDescriptionId() > 0) {
            mTextSettingDescription!!.setText(item.getDescriptionId())
        } else {
            mTextSettingDescription!!.text = mItem!!.selectedValue.toString() + mItem!!.units
        }
    }

    override fun onClick(clicked: View) {
        adapter.onSliderClick(mItem!!, adapterPosition)
    }
}

