package org.dolphinemu.dolphinemu.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.ImageView
import androidx.annotation.Keep
import coil3.imageLoader
import coil3.Image
import coil3.toBitmap
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.utils.CoverHelper
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization
import org.dolphinemu.dolphinemu.DolphinApplication
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Keep
class GameFile private constructor(private val pointer: Long) {
    private var sName: String? = null

    val title: String?
        get() {
            if (sName == null) sName = getName()
            return sName
        }

    external fun getPlatform(): Int

    external fun getName(): String

    external fun getDescription(): String

    external fun getCompany(): String

    external fun getCountry(): Int

    external fun getRegion(): Int

    external fun getPath(): String

    external fun getTitlePath(): String

    external fun getGameId(): String

    external fun getGameTdbId(): String

    external fun getDiscNumber(): Int

    external fun getRevision(): Int

    external fun getBlobTypeString(): String

    external fun shouldShowFileFormatDetails(): Boolean

    external fun getFileSize(): Long

    external fun getBlobType(): Int

    external fun shouldAllowConversion(): Boolean

    external fun isDatelDisc(): Boolean

    private external fun getBanner(): IntArray

    private external fun getBannerWidth(): Int

    private external fun getBannerHeight(): Int

    fun getCoverPath(context: Context): String {
        return DirectoryInitialization.getCacheDirectory(context) + "/GameCovers/" + getGameTdbId() + ".png"
    }

    val lastSavedState: String?
        get() {
            val numStates = 10
            val statePath =
                DirectoryInitialization.getUserDirectory() + "/StateSaves/"
            val gameId = getGameId()
            var lastModified: Long = 0
            var savedState: String? = null
            for (i in 0 until numStates) {
                val filename =
                    String.format("%s%s.s%02d", statePath, gameId, i)
                val stateFile = File(filename)
                if (stateFile.exists()) {
                    if (stateFile.lastModified() > lastModified) {
                        savedState = filename
                        lastModified = stateFile.lastModified()
                    }
                }
            }
            return savedState
        }

    private var coverType = COVER_UNKNOWN

    fun loadGameBanner(imageView: ImageView) {
        if (coverType == COVER_UNKNOWN) {
            if (loadFromCache(imageView)) {
                coverType = COVER_CACHE
                return
            }

            coverType = COVER_NONE
            loadFromNetwork(imageView, object : Callback {
                override fun onSuccess() {
                    coverType = COVER_CACHE
                    CoverHelper.saveCover(
                        (imageView.drawable as BitmapDrawable).bitmap,
                        getCoverPath(imageView.context)
                    )
                }

                override fun onError(e: Exception) {
                    if (loadFromISO(imageView)) {
                        coverType = COVER_CACHE
                    } else if (NativeLibrary.isNetworkConnected(imageView.context)) {
                        // save placeholder to file
                        CoverHelper.saveCover(
                            (imageView.drawable as BitmapDrawable).bitmap,
                            getCoverPath(imageView.context)
                        )
                    }
                }
            })
        } else if (coverType == COVER_CACHE) {
            loadFromCache(imageView)
        } else {
            imageView.setImageResource(R.drawable.no_banner)
        }
    }

    private fun loadFromCache(imageView: ImageView): Boolean {
        val file = File(getCoverPath(imageView.context))
        if (file.exists()) {
            imageView.setImageURI(Uri.parse("file://" + getCoverPath(imageView.context)))
            return true
        }
        return false
    }

    private fun loadFromNetwork(imageView: ImageView, callback: Callback) {
        GlobalScope(Dispatchers.Main).launch {
            var request = ImageRequest.Builder(DolphinApplication.getAppContext())
                    .data(CoverHelper.buildGameTDBUrl(this, null))
                    .build()

            var result = withContext(Dispatchers.IO) { DolphinApplication.getAppContext().imageLoader.execute(request) }
        
            if (result is SuccessResult) {
                imageView.setImageBitmap((result.image as Image).toBitmap())
                callback.onSuccess()
            } else {
                val id = getGameTdbId()
                var region: String? = null
                if (id.length < 3) {
                    callback.onError(Exception("failed to load game banner"))
                    return@launch
                } else {
                    region = when (id[3]) {
                        'E' -> "US"
                        'J' -> "JA"
                    } else -> {
                      callback.onError(Exception("failed to load game banner"))
                      return@launch
                    }
                }      
                // TODO(Ishan09811): try to load with region once
            }
        }
    }

    private fun loadFromISO(imageView: ImageView): Boolean {
        val vector = getBanner()
        val width = getBannerWidth()
        val height = getBannerHeight()
        if (vector.isNotEmpty() && width > 0 && height > 0) {
            val file = File(getCoverPath(imageView.context))
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(vector, 0, width, 0, 0, width, height)
            try {
                val out = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.close()
            } catch (e: Exception) {
                return false
            }
            return loadFromCache(imageView)
        }
        return false
    }

    companion object {
        private const val COVER_UNKNOWN = 0
        private const val COVER_CACHE = 1
        private const val COVER_NONE = 2

        @JvmStatic
        external fun parse(path: String): GameFile?
    }

    interface Callback {
        fun onSuccess()
        fun onError(e: Exception)
    }
}
