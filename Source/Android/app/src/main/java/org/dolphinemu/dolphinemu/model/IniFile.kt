package org.dolphinemu.dolphinemu.model

import androidx.annotation.Keep

class IniFile {
    @Keep
    private val pointer: Long = newIniFile()

    external fun finalize()

    external fun loadFile(filename: String?, keep_current_data: Boolean): Boolean
    external fun saveFile(filename: String?): Boolean

    external fun setString(section: String?, key: String?, value: String?)
    external fun getString(section: String?, key: String?, default_value: String?): String?

    external fun delete(section: String?, key: String?): Boolean

    private external fun newIniFile(): Long
}
