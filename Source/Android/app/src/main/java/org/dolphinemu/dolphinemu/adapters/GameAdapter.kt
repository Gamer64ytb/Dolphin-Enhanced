package org.dolphinemu.dolphinemu.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.activities.EmulationActivity.Companion.launch
import org.dolphinemu.dolphinemu.dialogs.GameDetailsDialog.Companion.newInstance
import org.dolphinemu.dolphinemu.model.GameFile
import org.dolphinemu.dolphinemu.viewholders.GameViewHolder

class GameAdapter : RecyclerView.Adapter<GameViewHolder>(), View.OnClickListener,
    OnLongClickListener {
    private var mResourceId = 0
    private var mGameFiles: List<GameFile>

    /**
     * Initializes the adapter's observer, which watches for changes to the dataset. The adapter will
     * display no data until swapDataSet is called.
     */
    init {
        mGameFiles = ArrayList()
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
        return mResourceId
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
        val gameFile = mGameFiles[position]
        gameFile.loadGameBanner(holder.imageScreenshot)

        holder.textGameTitle.text = gameFile.title
        holder.textCompany.text = gameFile.company

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
        var platform = gameFile.platform
        var country = gameFile.country
        val discNumber = gameFile.discNumber + 1
        if (platform == 2) {
            // WiiWAD, Virtual Console
            val gameId = gameFile.gameId
            when (gameId[0]) {
                'N' ->           // N64
                    platform = 3

                'F' ->           // NES
                    platform = 4

                'L' ->           // SMS
                    platform = 5

                'M' ->           // SMD
                    platform = 6

                'C' ->           // C64
                    platform = 7

                'J' ->           // SNES
                    platform = 8
            }
        }
        val discInfo = if (discNumber > 1) "Disc-$discNumber" else ""
        if (platform < 0 || platform >= platforms.size) platform = 2
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
        return mGameFiles.size
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
        mGameFiles = gameFiles
        notifyDataSetChanged()
    }

    fun setResourceId(resId: Int) {
        mResourceId = resId
    }

    /**
     * Launches the game that was clicked on.
     *
     * @param view The card representing the game the user wants to play.
     */
    override fun onClick(view: View) {
        val holder = view.tag as GameViewHolder
        launch(view.context, holder.gameFile, null)
    }

    /**
     * Launches the details activity for this Game, using an ID stored in the
     * details button's Tag.
     *
     * @param view The Card button that was long-clicked.
     */
    override fun onLongClick(view: View): Boolean {
        val activity = view.context as FragmentActivity
        val holder = view.tag as GameViewHolder
        newInstance(holder.gameFile.path).show(
            activity.supportFragmentManager, "GameDetailsDialog"
        )
        return true
    }
}
