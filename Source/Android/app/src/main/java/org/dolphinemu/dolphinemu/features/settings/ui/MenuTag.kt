package org.dolphinemu.dolphinemu.features.settings.ui

enum class MenuTag {
    CONFIG("config"),
    CONFIG_GENERAL("config_general"),
    CONFIG_INTERFACE("config_interface"),
    CONFIG_INPUT("config_input"),
    CONFIG_GAME_CUBE("config_gamecube"),
    CONFIG_WII("config_wii"),
    WIIMOTE("wiimote"),
    WIIMOTE_EXTENSION("wiimote_extension"),
    GCPAD_TYPE("gc_pad_type"),
    GRAPHICS("graphics"),
    HACKS("hacks"),
    DEBUG("debug"),
    ENHANCEMENTS("enhancements"),
    GCPAD_1("gcpad", 0),
    GCPAD_2("gcpad", 1),
    GCPAD_3("gcpad", 2),
    GCPAD_4("gcpad", 3),
    WIIMOTE_1("wiimote", 4),
    WIIMOTE_2("wiimote", 5),
    WIIMOTE_3("wiimote", 6),
    WIIMOTE_4("wiimote", 7),
    WIIMOTE_EXTENSION_1("wiimote_extension", 4),
    WIIMOTE_EXTENSION_2("wiimote_extension", 5),
    WIIMOTE_EXTENSION_3("wiimote_extension", 6),
    WIIMOTE_EXTENSION_4("wiimote_extension", 7),
    GPU_DRIVERS("gpu_drivers");

    var tag: String
        private set
    var subType = -1
        private set

    constructor(tag: String) {
        this.tag = tag
    }

    constructor(tag: String, subtype: Int) {
        this.tag = tag
        this.subType = subtype
    }

    override fun toString(): String {
        if (subType != -1) {
            return "$tag|$subType"
        }

        return tag
    }

    val isGCPadMenu: Boolean
        get() = this == GCPAD_1 || this == GCPAD_2 || this == GCPAD_3 || this == GCPAD_4

    val isWiimoteMenu: Boolean
        get() = this == WIIMOTE_1 || this == WIIMOTE_2 || this == WIIMOTE_3 || this == WIIMOTE_4

    val isWiimoteExtensionMenu: Boolean
        get() = this == WIIMOTE_EXTENSION_1 || this == WIIMOTE_EXTENSION_2 || this == WIIMOTE_EXTENSION_3 || this == WIIMOTE_EXTENSION_4

    companion object {
        @JvmStatic
        fun getGCPadMenuTag(subtype: Int): MenuTag {
            return getMenuTag("gcpad", subtype)
        }

        @JvmStatic
        fun getWiimoteMenuTag(subtype: Int): MenuTag {
            return getMenuTag("wiimote", subtype)
        }

        @JvmStatic
        fun getWiimoteExtensionMenuTag(subtype: Int): MenuTag {
            return getMenuTag("wiimote_extension", subtype)
        }

        fun getMenuTag(menuTagStr: String?): MenuTag? {
            if (menuTagStr.isNullOrEmpty()) {
                return null
            }
            var tag = menuTagStr
            var subtype = -1
            val sep = menuTagStr.indexOf('|')
            if (sep != -1) {
                tag = menuTagStr.substring(0, sep)
                subtype = menuTagStr.substring(sep + 1).toInt()
            }
            return getMenuTag(tag, subtype)
        }

        private fun getMenuTag(tag: String, subtype: Int): MenuTag {
            for (menuTag in values()) {
                if (menuTag.tag == tag && menuTag.subType == subtype) return menuTag
            }

            throw IllegalArgumentException(
                "You are asking for a menu that is not available or " +
                        "passing a wrong subtype"
            )
        }
    }
}
