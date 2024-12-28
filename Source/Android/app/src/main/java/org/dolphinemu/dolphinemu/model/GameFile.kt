package org.dolphinemu.dolphinemu.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.ImageView
import androidx.annotation.Keep
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.utils.CoverHelper
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization
import java.io.File
import java.io.FileOutputStream

class GameFile
@Keep private constructor(@field:Keep private val pointer: Long) {
    private var sName: String? = null

    val title: String?
        get() {
            if (sName == null) sName = name
            return sName
        }

    val platform: Int
        external get

    val name: String?
        external get

    val description: String?
        external get

    val company: String?
        external get

    val country: Int
        external get

    val region: Int
        external get

    val path: String?
        external get

    val titlePath: String?
        external get

    val gameId: String
        external get

    val gameTdbId: String
        external get

    val discNumber: Int
        external get

    val revision: Int
        external get

    val blobType: Int
        external get

    external fun shouldAllowConversion(): Boolean

    val isDatelDisc: Boolean
        external get

    val banner: IntArray
        external get

    val bannerWidth: Int
        external get

    val bannerHeight: Int
        external get

    fun getCoverPath(context: Context): String {
        return DirectoryInitialization.getCacheDirectory(context) + "/GameCovers/" + gameTdbId + ".png"
    }

    val lastSavedState: String?
        get() {
            val numStates = 10
            val statePath =
                DirectoryInitialization.getUserDirectory() + "/StateSaves/"
            val gameId = gameId
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

    private var mCoverType = COVER_UNKNOWN

    fun loadGameBanner(imageView: ImageView) {
        if (mCoverType == COVER_UNKNOWN) {
            if (loadFromCache(imageView)) {
                mCoverType = COVER_CACHE
                return
            }

            mCoverType = COVER_NONE
            loadFromNetwork(imageView, object : Callback {
                override fun onSuccess() {
                    mCoverType = COVER_CACHE
                    CoverHelper.saveCover(
                        (imageView.drawable as BitmapDrawable).bitmap,
                        getCoverPath(imageView.context)
                    )
                }

                override fun onError(e: Exception) {
                    if (loadFromISO(imageView)) {
                        mCoverType = COVER_CACHE
                    } else if (NativeLibrary.isNetworkConnected(imageView.context)) {
                        // save placeholder to file
                        CoverHelper.saveCover(
                            (imageView.drawable as BitmapDrawable).bitmap,
                            getCoverPath(imageView.context)
                        )
                    }
                }
            })
        } else if (mCoverType == COVER_CACHE) {
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
        Picasso.get()
            .load(CoverHelper.buildGameTDBUrl(this, null))
            .placeholder(R.drawable.no_banner)
            .error(R.drawable.no_banner)
            .into(imageView, object : Callback {
                override fun onSuccess() {
                    callback.onSuccess()
                }

                override fun onError(e: Exception) {
                    val id: String = gameTdbId
                    var region: String? = null
                    region = if (id.length < 3) {
                        callback.onError(e)
                        return
                    } else if (id[3] != 'E') {
                        "US"
                    } else if (id[3] != 'J') {
                        "JA"
                    } else {
                        callback.onError(e)
                        return
                    }
                    Picasso.get()
                        .load(CoverHelper.buildGameTDBUrl(this@GameFile, region))
                        .placeholder(R.drawable.no_banner)
                        .error(R.drawable.no_banner)
                        .into(imageView, callback)
                }
            })
    }

    private fun loadFromISO(imageView: ImageView): Boolean {
        val vector = banner
        val width = bannerWidth
        val height = bannerHeight
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
}
