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
    private var item: SliderSetting? = null

    private var name: TextView? = null
    private var value: TextView? = null
    private var seekBar: SeekBar? = null

    override fun findViews(root: View) {
        name = root.findViewById(R.id.text_setting_name)
        value = root.findViewById(R.id.text_setting_value)
        seekBar = root.findViewById(R.id.seekbar)
    }

    override fun bind(item: SettingsItem) {
        this.item = item as SliderSetting
        name!!.setText(item.getNameId())
        seekBar!!.max = this.item!!.max
        seekBar!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                var progress = progress
                if (this@SeekbarViewHolder.item!!.max > 99) progress = (progress / 5) * 5
                value!!.text = progress.toString() + this@SeekbarViewHolder.item!!.units
                if (progress != this@SeekbarViewHolder.item!!.selectedValue) {
                    this@SeekbarViewHolder.item!!.setSelectedValue(progress)
                    adapter.onSeekbarClick(this@SeekbarViewHolder.item!!, adapterPosition, progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })
        seekBar!!.progress = this.item!!.selectedValue
    }

    override fun onClick(clicked: View) {
    }
}
