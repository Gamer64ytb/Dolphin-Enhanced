package org.dolphinemu.dolphinemu.features.settings.model.view

import org.dolphinemu.dolphinemu.features.settings.model.Setting
import org.dolphinemu.dolphinemu.features.settings.ui.MenuTag

class SubmenuSetting(
    key: String?, setting: Setting?, titleId: Int, descriptionId: Int,
    val menuKey: MenuTag
) : SettingsItem(key, null, setting, titleId, descriptionId) {
    override fun getType(): Int {
        return TYPE_SUBMENU
    }
}
