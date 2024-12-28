package org.dolphinemu.dolphinemu.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.squareup.picasso.Picasso
import com.squareup.picasso.Request
import com.squareup.picasso.RequestHandler
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.activities.ConvertActivity
import org.dolphinemu.dolphinemu.activities.EditorActivity
import org.dolphinemu.dolphinemu.activities.EmulationActivity.Companion.launch
import org.dolphinemu.dolphinemu.features.settings.ui.MenuTag
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsActivity.Companion.launch
import org.dolphinemu.dolphinemu.model.GameFile
import org.dolphinemu.dolphinemu.services.GameFileCacheService
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization
import java.io.File

// GameBannerRequestHandler is only used there, so let's join it into one file
internal class GameBannerRequestHandler(private val gameFile: GameFile) : RequestHandler() {
    override fun canHandleRequest(data: Request): Boolean {
        return true
    }

    override fun load(request: Request, networkPolicy: Int): Result? {
        val vector = gameFile.banner
        val width = gameFile.bannerWidth
        val height = gameFile.bannerHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(vector, 0, width, 0, 0, width, height)
        return Result(bitmap, Picasso.LoadedFrom.DISK)
    }
}

class GameDetailsDialog : DialogFragment() {
    private var gameId: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val gameFile =
            GameFileCacheService.addOrGet(requireArguments().getString(ARG_GAME_PATH))

        gameId = gameFile.gameId

        val builder = AlertDialog.Builder(activity)
        val contents = requireActivity().layoutInflater
            .inflate(R.layout.dialog_game_details, null) as ViewGroup

        // game title
        val textGameTitle = contents.findViewById<TextView>(R.id.text_game_title)
        textGameTitle.text = gameFile.title

        // game filename
        var gameId = gameFile.gameId
        if (gameFile.platform > 0) {
            gameId += ", " + gameFile.titlePath
        }
        val textGameFilename = contents.findViewById<TextView>(R.id.text_game_filename)
        textGameFilename.text = gameId

        //
        val buttonConvert = contents.findViewById<Button>(R.id.button_convert)
        buttonConvert.setOnClickListener {
            this.dismiss()
            ConvertActivity.launch(context, gameFile.path)
        }
        buttonConvert.isEnabled = gameFile.shouldAllowConversion()

        val buttonDeleteSetting = contents.findViewById<Button>(R.id.button_delete_setting)
        buttonDeleteSetting.setOnClickListener {
            this.dismiss()
            this.deleteGameSetting(context)
        }
        buttonDeleteSetting.isEnabled = gameSettingExists()

        val buttonCheatCode = contents.findViewById<Button>(R.id.button_cheat_code)
        buttonCheatCode.setOnClickListener {
            this.dismiss()
            EditorActivity.launch(requireContext(), gameFile.path)
        }

        //
        val buttonWiimote = contents.findViewById<Button>(R.id.button_wiimote_settings)
        buttonWiimote.setOnClickListener {
            this.dismiss()
            launch(requireContext(), MenuTag.WIIMOTE, gameFile.gameId)
        }
        buttonWiimote.isEnabled = gameFile.platform > 0

        val buttonGCPad = contents.findViewById<Button>(R.id.button_gcpad_settings)
        buttonGCPad.setOnClickListener {
            this.dismiss()
            launch(requireContext(), MenuTag.GCPAD_TYPE, gameFile.gameId)
        }

        //
        val buttonGameSetting = contents.findViewById<Button>(R.id.button_game_setting)
        buttonGameSetting.setOnClickListener {
            this.dismiss()
            launch(requireContext(), MenuTag.CONFIG, gameFile.gameId)
        }

        val buttonLaunch = contents.findViewById<Button>(R.id.button_quick_load)
        buttonLaunch.setOnClickListener {
            this.dismiss()
            launch(requireContext(), gameFile, gameFile.lastSavedState)
        }

        val imageGameScreen = contents.findViewById<ImageView>(R.id.image_game_screen)
        loadGameBanner(imageGameScreen, gameFile)

        builder.setView(contents)
        return builder.create()
    }

    private fun gameSettingExists(): Boolean {
        val path = DirectoryInitialization.getLocalSettingFile(gameId)
        val gameSettingsFile = File(path)
        return gameSettingsFile.exists()
    }

    private fun deleteGameSetting(context: Context?) {
        val path = DirectoryInitialization.getLocalSettingFile(gameId)
        val gameSettingsFile = File(path)
        if (gameSettingsFile.exists()) {
            if (gameSettingsFile.delete()) {
                Toast.makeText(context, "Cleared settings for $gameId", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Unable to clear settings for $gameId", Toast.LENGTH_SHORT)
                    .show()
            }
        } else {
            Toast.makeText(context, "No game settings to delete", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val ARG_GAME_PATH = "game_path"

        private fun loadGameBanner(imageView: ImageView, gameFile: GameFile) {
            val picassoInstance = Picasso.Builder(imageView.context)
                .addRequestHandler(GameBannerRequestHandler(gameFile))
                .build()

            picassoInstance
                .load(Uri.parse("file://" + gameFile.getCoverPath(imageView.context)))
                .fit()
                .noFade()
                .noPlaceholder()
                .config(Bitmap.Config.RGB_565)
                .error(R.drawable.no_banner)
                .into(imageView)
        }

        @JvmStatic
        fun newInstance(gamePath: String?): GameDetailsDialog {
            val fragment = GameDetailsDialog()

            val arguments = Bundle()
            arguments.putString(ARG_GAME_PATH, gamePath)
            fragment.arguments = arguments

            return fragment
        }
    }
}
