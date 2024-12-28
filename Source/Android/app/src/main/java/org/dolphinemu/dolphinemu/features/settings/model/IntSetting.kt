package org.dolphinemu.dolphinemu.features.settings.model

class IntSetting(key: String?, section: String?, var value: Int) :
    Setting(key, section) {
    override fun getValueAsString(): String {
        return value.toString()
    }
}
