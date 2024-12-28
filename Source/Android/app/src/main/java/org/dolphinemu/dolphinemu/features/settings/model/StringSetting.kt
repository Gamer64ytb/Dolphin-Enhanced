package org.dolphinemu.dolphinemu.features.settings.model

class StringSetting(key: String?, section: String?, var value: String?) :
    Setting(key, section) {
    override fun getValueAsString(): String {
        return if (value == null) "" else value!!
    }
}
