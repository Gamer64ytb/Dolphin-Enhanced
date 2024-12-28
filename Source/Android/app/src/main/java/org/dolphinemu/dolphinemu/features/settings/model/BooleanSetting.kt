package org.dolphinemu.dolphinemu.features.settings.model

class BooleanSetting(key: String?, section: String?, var value: Boolean) :
    Setting(key, section) {
    override fun getValueAsString(): String {
        return if (value) "True" else "False"
    }
}
