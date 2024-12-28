package org.dolphinemu.dolphinemu.viewholders

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.model.GameFile

/**
 * A simple class that stores references to views so that the GameAdapter doesn't need to
 * keep calling findViewById(), which is expensive.
 */
class GameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    var imageScreenshot: ImageView
    var textGameTitle: TextView
    var textCompany: TextView
    var textPlatform: TextView
    var textCountry: TextView
    var textGameDisc: TextView

    var gameFile: GameFile? = null

    init {
        itemView.tag = this

        imageScreenshot = itemView.findViewById(R.id.image_game_screen)
        textGameTitle = itemView.findViewById(R.id.text_game_title)
        textCompany = itemView.findViewById(R.id.text_company)
        textPlatform = itemView.findViewById(R.id.text_platform)
        textCountry = itemView.findViewById(R.id.text_game_country)
        textGameDisc = itemView.findViewById(R.id.text_game_disc)
    }
}
