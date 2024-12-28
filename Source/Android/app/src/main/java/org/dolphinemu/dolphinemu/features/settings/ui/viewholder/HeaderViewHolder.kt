package org.dolphinemu.dolphinemu.features.settings.ui.viewholder

import android.view.View
import android.widget.TextView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsAdapter

class HeaderViewHolder(itemView: View, adapter: SettingsAdapter) :
    SettingViewHolder(itemView, adapter) {
    private var mHeaderName: TextView? = null

    init {
        itemView.setOnClickListener(null)
    }

    override fun findViews(root: View) {
        mHeaderName = root.findViewById(R.id.text_header_name)
    }

    override fun bind(item: SettingsItem) {
        mHeaderName!!.setText(item.nameId)
    }

    override fun onClick(clicked: View) {
        // no-op
    }
}
