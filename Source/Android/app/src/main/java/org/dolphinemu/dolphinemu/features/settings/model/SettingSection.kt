package org.dolphinemu.dolphinemu.features.settings.model

/**
 * A semantically-related group of Settings objects. These Settings are
 * internally stored as a HashMap.
 */
class SettingSection

/**
 * Create a new SettingSection with no Settings in it.
 *
 * @param name The header of this section; e.g. [Core] or [Enhancements] without the brackets.
 */(val name: String) {
    val settings: HashMap<String, Setting> = HashMap()

    /**
     * Convenience method; inserts a value directly into the backing HashMap.
     *
     * @param setting The Setting to be inserted.
     */
    fun putSetting(setting: Setting) {
        settings[setting.key] = setting
    }

    /**
     * Convenience method; gets a value directly from the backing HashMap.
     *
     * @param key Used to retrieve the Setting.
     * @return A Setting object (you should probably cast this before using)
     */
    fun getSetting(key: String): Setting? {
        return settings[key]
    }

    fun mergeSection(settingSection: SettingSection) {
        for (setting in settingSection.settings.values) {
            putSetting(setting)
        }
    }
}
