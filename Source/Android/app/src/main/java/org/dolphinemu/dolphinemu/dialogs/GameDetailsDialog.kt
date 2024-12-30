package org.dolphinemu.dolphinemu.dialogs

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.activities.ConvertActivity
import org.dolphinemu.dolphinemu.activities.EditorActivity
import org.dolphinemu.dolphinemu.activities.EmulationActivity
import org.dolphinemu.dolphinemu.features.settings.ui.MenuTag
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsActivity.Companion.launch
import org.dolphinemu.dolphinemu.model.GameFile
import org.dolphinemu.dolphinemu.services.GameFileCacheService
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization
import java.io.File

class GameDetailsDialog(context: Context, gamePath: String?) : BottomSheetDialog(context) {
    private var gameId: String? = null

    init {
        setContentView(R.layout.dialog_game_details)
        val gameFile = GameFileCacheService.addOrGet(gamePath)
        gameId = gameFile.gameId

        // Game title
        findViewById<TextView>(R.id.text_game_title)?.text = gameFile.title

        // Game filename with platform info
        findViewById<TextView>(R.id.text_game_filename)?.text = buildString {
            append(gameFile.gameId)
            if (gameFile.platform > 0) {
                append(", ")
                append(gameFile.titlePath)
            }
        }

        // Set up buttons
        findViewById<Button>(R.id.button_convert)?.apply {
            setOnClickListener {
                dismiss()
                ConvertActivity.launch(context, gameFile.path)
            }
            isEnabled = gameFile.shouldAllowConversion()
        }

        findViewById<Button>(R.id.button_delete_setting)?.apply {
            setOnClickListener {
                dismiss()
                deleteGameSetting(context)
            }
            isEnabled = gameSettingExists()
        }

        findViewById<Button>(R.id.button_cheat_code)?.apply {
            setOnClickListener {
                dismiss()
                EditorActivity.launch(context, gameFile.path)
            }
        }

        findViewById<Button>(R.id.button_wiimote_settings)?.apply {
            setOnClickListener {
                dismiss()
                launch(context, MenuTag.WIIMOTE, gameFile.gameId)
            }
            isEnabled = gameFile.platform > 0
        }

        findViewById<Button>(R.id.button_gcpad_settings)?.apply {
            setOnClickListener {
                dismiss()
                launch(context, MenuTag.GCPAD_TYPE, gameFile.gameId)
            }
        }

        findViewById<Button>(R.id.button_game_setting)?.apply {
            setOnClickListener {
                dismiss()
                launch(context, MenuTag.CONFIG, gameFile.gameId)
            }
        }

        findViewById<Button>(R.id.button_quick_load)?.apply {
            setOnClickListener {
                dismiss()
                EmulationActivity.launch(context, gameFile, gameFile.lastSavedState)
            }
        }

        // Load game banner
        findViewById<ImageView>(R.id.image_game_screen)?.let { imageView ->
            loadGameBanner(imageView, gameFile)
        }

        // Load bottom sheet dialog with custom background colors and rounded corners
        setOnShowListener { dialog ->
            (dialog as BottomSheetDialog).findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )?.background = ContextCompat.getDrawable(context, R.drawable.rounded_bottom_sheet)
        }
    }

    override fun onStart() {
        super.onStart()

        val bottomSheet = findViewById<ViewGroup>(com.google.android.material.R.id.design_bottom_sheet)!!
        val behavior = BottomSheetBehavior.from(bottomSheet)

        val displayMetrics = context.resources.displayMetrics
        val isLandscape = context.resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE

        // Set max height based on orientation
        // Without this, the bottom sheet will not show up on landscape due to overflow
        if (isLandscape) {
            bottomSheet.layoutParams.height = (displayMetrics.heightPixels * 0.9).toInt()
        }

        behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            peekHeight = ViewGroup.LayoutParams.WRAP_CONTENT
            isHideable = true // Allows dismissing the bottom sheet on swipe which isn't default behavior
            skipCollapsed = true

            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        dismiss()
                    }
                }
                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            })
        }

        bottomSheet.layoutParams = bottomSheet.layoutParams
    }
    private fun gameSettingExists(): Boolean {
        val path = DirectoryInitialization.getLocalSettingFile(gameId)
        val gameSettingsFile = File(path)
        return gameSettingsFile.exists()
    }

    private fun deleteGameSetting(context: Context) {
        val path = DirectoryInitialization.getLocalSettingFile(gameId)
        val gameSettingsFile = File(path)
        if (gameSettingsFile.exists()) {
            if (gameSettingsFile.delete()) {
                Toast.makeText(context, "Cleared settings for $gameId", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Unable to clear settings for $gameId", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "No game settings to delete", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private fun loadGameBanner(imageView: ImageView, gameFile: GameFile) {
            val coverPath = gameFile.getCoverPath(imageView.context)
            val bitmap = BitmapFactory.decodeFile(coverPath)
            imageView.setImageBitmap(bitmap)
        }
    }
}
