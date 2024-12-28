package org.dolphinemu.dolphinemu.ui.platform

/**
 * Enum to represent platform (eg GameCube, Wii).
 */
enum class Platform
    (private val value: Int, val headerName: String) {
    GAMECUBE(0, "GameCube Games"),
    WII(1, "Wii Games"),
    WIIWARE(2, "WiiWare Games");

    fun toInt(): Int {
        return value
    }

    companion object {
        fun fromInt(i: Int): Platform {
            return values()[i]
        }

        fun fromNativeInt(i: Int): Platform {
            // TODO: Proper support for DOL and ELF files
            val inRange = i >= 0 && i < values().size
            return values()[if (inRange) i else WIIWARE.value]
        }

        fun fromPosition(position: Int): Platform {
            return values()[position]
        }
    }
}
