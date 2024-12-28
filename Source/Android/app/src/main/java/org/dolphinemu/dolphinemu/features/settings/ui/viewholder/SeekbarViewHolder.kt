package org.dolphinemu.dolphinemu.features.settings.ui.viewholder

import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem
import org.dolphinemu.dolphinemu.features.settings.model.view.SliderSetting
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsAdapter

class SeekbarViewHolder(itemView: View, adapter: SettingsAdapter) :
    SettingViewHolder(itemView, adapter) {
    private var mItem: SliderSetting? = null

    private var mName: TextView? = null
    private var mValue: TextView? = null
    private var mSeekBar: SeekBar? = null

    override fun findViews(root: View) {
        mName = root.findViewById(R.id.text_setting_name)
        mValue = root.findViewById(R.id.text_setting_value)
        mSeekBar = root.findViewById(R.id.seekbar)
    }

    override fun bind(item: SettingsItem) {
        mItem = item as SliderSetting
        mName!!.setText(item.getNameId())
        mSeekBar!!.max = mItem!!.max
        mSeekBar!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                var progress = progress
                if (mItem!!.max > 99) progress = (progress / 5) * 5
                mValue!!.text = progress.toString() + mItem!!.units
                if (progress != mItem!!.selectedValue) {
                    mItem!!.setSelectedValue(progress)
                    adapter.onSeekbarClick(mItem!!, adapterPosition, progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })
        mSeekBar!!.progress = mItem!!.selectedValue
    }

    override fun onClick(clicked: View) {
    }
}
