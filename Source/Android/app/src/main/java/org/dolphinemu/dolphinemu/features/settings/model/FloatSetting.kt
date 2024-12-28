package org.dolphinemu.dolphinemu.features.settings.model

class FloatSetting(key: String?, section: String?, var value: Float) :
    Setting(key, section) {
    override fun getValueAsString(): String {
        return value.toString()
    }
}
