package org.dolphinemu.dolphinemu.features.settings.model.view

import org.dolphinemu.dolphinemu.features.settings.model.Setting

class HeaderSetting(key: String?, setting: Setting?, titleId: Int, descriptionId: Int) :
    SettingsItem(key, null, setting, titleId, descriptionId) {
    override fun getType(): Int {
        return TYPE_HEADER
    }
}
