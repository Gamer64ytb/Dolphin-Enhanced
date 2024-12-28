package org.dolphinemu.dolphinemu.utils

import android.graphics.Bitmap
import org.dolphinemu.dolphinemu.model.GameFile
import java.io.FileOutputStream

object CoverHelper {
    fun buildGameTDBUrl(game: GameFile, region: String?): String {
        var region = region
        val baseUrl = "https://art.gametdb.com/wii/cover/%s/%s.png"
        var id: String? = game.gameTdbId
        if (region == null) {
            region = getRegion(game)
        } else {
            id = toRegion(id, region)
        }
        return String.format(baseUrl, region, id)
    }

    private fun toRegion(id: String?, region: String): String? {
        if (id == null || id.length < 4) {
            // ignore
        } else if ("JA" == region) {
            if (id[3] != 'J') {
                return id.substring(0, 3) + "J" + id.substring(4)
            }
        } else if ("US" == region) {
            if (id[3] != 'E') {
                return id.substring(0, 3) + "E" + id.substring(4)
            }
        }
        return id
    }

    private fun getRegion(game: GameFile): String {
        val region = when (game.region) {
            0 -> "JA"
            1 -> "US"
            4 -> "KO"
            2 -> when (game.country) {
                2 -> "DE"
                3 -> "FR"
                4 -> "ES"
                5 -> "IT"
                6 -> "NL"
                1 -> "EN"
                else -> "EN"
            }

            3 -> "EN"
            else -> "EN"
        }
        return region
    }

    fun saveCover(cover: Bitmap, path: String?) {
        try {
            val out = FileOutputStream(path)
            cover.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.close()
        } catch (e: Exception) {
            // Do nothing
        }
    }
}
