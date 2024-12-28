package org.dolphinemu.dolphinemu.features.settings.ui.viewholder

import android.view.View
import android.widget.TextView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsAdapter

class HeaderViewHolder(itemView: View, adapter: SettingsAdapter) :
    SettingViewHolder(itemView, adapter) {
    private var headerName: TextView? = null

    init {
        itemView.setOnClickListener(null)
    }

    override fun findViews(root: View) {
        headerName = root.findViewById(R.id.text_header_name)
    }

    override fun bind(item: SettingsItem) {
        headerName!!.setText(item.nameId)
    }

    override fun onClick(clicked: View) {
        // no-op
    }
}
