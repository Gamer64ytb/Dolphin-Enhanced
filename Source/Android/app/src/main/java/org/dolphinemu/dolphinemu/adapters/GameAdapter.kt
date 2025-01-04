package org.dolphinemu.dolphinemu.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.activities.EmulationActivity.Companion.launch
import org.dolphinemu.dolphinemu.dialogs.GameDetailsDialog
import org.dolphinemu.dolphinemu.model.GameFile
import org.dolphinemu.dolphinemu.ui.platform.Platform
import org.dolphinemu.dolphinemu.viewholders.GameViewHolder

class GameAdapter : RecyclerView.Adapter<GameViewHolder>(), View.OnClickListener,
    OnLongClickListener {
    private var resourceId = 0
    private var allGameFiles: List<GameFile> = ArrayList()
    private var filteredGameFiles: List<GameFile> = ArrayList()
    private var currentPlatform: Int = Platform.GAMECUBE.toInt()

    /**
     * Initializes the adapter's observer, which watches for changes to the dataset. The adapter will
     * display no data until swapDataSet is called.
     */
    init {
        allGameFiles = ArrayList()
    }

    /**
     * Called by the LayoutManager when it is necessary to create a new view.
     *
     * @param parent   The RecyclerView (I think?) the created view will be thrown into.
     * @param viewType Not used here, but useful when more than one type of child will be used in the RecyclerView.
     * @return The created ViewHolder with references to all the child view's members.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        // Create a new view.
        val gameCard = LayoutInflater.from(parent.context)
            .inflate(viewType, parent, false)

        gameCard.setOnClickListener(this)
        gameCard.setOnLongClickListener(this)

        // Use that view to create a ViewHolder.
        return GameViewHolder(gameCard)
    }

    override fun getItemViewType(position: Int): Int {
        return resourceId
    }

    /**
     * Called by the LayoutManager when a new view is not necessary because we can recycle
     * an existing one (for example, if a view just scrolled onto the screen from the bottom, we
     * can use the view that just scrolled off the top instead of inflating a new one.)
     *
     * @param holder   A ViewHolder representing the view we're recycling.
     * @param position The position of the 'new' view in the dataset.
     */
    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        val gameFile = filteredGameFiles[position]
        gameFile.loadGameBanner(holder.imageScreenshot)

        holder.textGameTitle.text = gameFile.title
        holder.textCompany.text = gameFile.getCompany()

        val platforms = intArrayOf(
            R.string.game_platform_ngc,
            R.string.game_platform_wii,
            R.string.game_platform_ware,
            R.string.game_platform_n64,
            R.string.game_platform_nes,
            R.string.game_platform_sms,
            R.string.game_platform_smd,
            R.string.game_platform_c64,
            R.string.game_platform_snes,
        )
        val context = holder.textPlatform.context
        val countryNames = context.resources.getStringArray(R.array.countryNames)
        var platform = gameFile.getPlatform()
        var country = gameFile.getCountry()
        val discNumber = gameFile.getDiscNumber() + 1

        // Handle Virtual Console games
        if (platform == Platform.WIIWARE.toInt()) {
            val gameId = gameFile.getGameId()
            if (gameId.isNotEmpty()) {
                when (gameId[0]) {
                    'N' -> platform = 3  // N64
                    'F' -> platform = 4  // NES
                    'L' -> platform = 5  // SMS
                    'M' -> platform = 6  // SMD
                    'C' -> platform = 7  // C64
                    'J' -> platform = 8  // SNES
                }
            }
        }
        val discInfo = if (discNumber > 1) "Disc-$discNumber" else ""
        if (platform < 0 || platform >= platforms.size) platform = Platform.WIIWARE.toInt()
        if (country < 0 || country >= countryNames.size) country = countryNames.size - 1
        holder.textPlatform.text =
            context.getString(platforms[platform], countryNames[country], discInfo)
        holder.textCountry.text = countryNames[country]
        holder.textGameDisc.text = discInfo
        holder.gameFile = gameFile
    }

    /**
     * Called by the LayoutManager to find out how much data we have.
     *
     * @return Size of the dataset.
     */
    override fun getItemCount(): Int {
        return filteredGameFiles.size
    }

    /**
     * Tell Android whether or not each item in the dataset has a stable identifier.
     *
     * @param hasStableIds ignored.
     */
    override fun setHasStableIds(hasStableIds: Boolean) {
        super.setHasStableIds(false)
    }

    /**
     * When a load is finished, call this to replace the existing data
     * with the newly-loaded data.
     */
    fun swapDataSet(gameFiles: List<GameFile>) {
        allGameFiles = gameFiles
        filterGamesByPlatform()
    }

    private fun filterGamesByPlatform() {
        filteredGameFiles = allGameFiles.filter { gameFile ->
            when (currentPlatform) {
                Platform.GAMECUBE.toInt() -> gameFile.getPlatform() == Platform.GAMECUBE.toInt()
                Platform.WII.toInt() -> gameFile.getPlatform() == Platform.WII.toInt()
                Platform.WIIWARE.toInt() -> {
                    val platform = gameFile.getPlatform()
                    platform == Platform.WIIWARE.toInt() ||
                    (platform in 3..8)
                }
                else -> false
            }
        }
        notifyDataSetChanged()
    }

    fun setResourceId(resId: Int) {
        resourceId = resId
    }

    /**
     * Launches the game that was clicked on.
     *
     * @param view The card representing the game the user wants to play.
     */
    override fun onClick(view: View) {
        val holder = view.tag as GameViewHolder
        launch(view.context, holder.gameFile!!, null)
    }

    /**
     * Launches the details activity for this Game, using an ID stored in the
     * details button's Tag.
     *
     * @param view The Card button that was long-clicked.
     */
    override fun onLongClick(view: View): Boolean {
        val context = view.context
        val holder = view.tag as GameViewHolder
        GameDetailsDialog(context, holder.gameFile!!.getPath()).show()
        return true
    }

    fun setCurrentPlatform(platform: Int) {
        currentPlatform = platform
        filterGamesByPlatform()
    }
}
