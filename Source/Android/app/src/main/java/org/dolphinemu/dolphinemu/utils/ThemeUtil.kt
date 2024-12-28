package org.dolphinemu.dolphinemu.utils

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import org.dolphinemu.dolphinemu.features.settings.model.Settings
import org.dolphinemu.dolphinemu.features.settings.utils.SettingsFile

object ThemeUtil {
    fun applyTheme() {
        val settings = Settings()

        try {
            settings.loadSettings(null)
            applyTheme(settings)
        } catch (e: Exception) {
            applyTheme(2)
        }
    }

    private fun applyTheme(designValue: Int) {
        when (designValue) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            2 -> AppCompatDelegate.setDefaultNightMode(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM else AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
        }
    }

    fun applyTheme(settings: Settings) {
        val design = settings.getSection(Settings.SECTION_INI_INTERFACE)
            .getSetting((SettingsFile.KEY_DESIGN))
        if (design == null) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            return
        }

        applyTheme(design.valueAsString.toInt())
    }
}
