package net.programmierecke.radiodroid2.service

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE
import androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE
import androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
import androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
import androidx.preference.PreferenceManager
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import jp.wasabeef.picasso.transformations.CropCircleTransformation
import jp.wasabeef.picasso.transformations.CropSquareTransformation
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.RadioDroidApp
import net.programmierecke.radiodroid2.Utils
import net.programmierecke.radiodroid2.Utils.resourceToUri
import net.programmierecke.radiodroid2.station.DataRadioStation
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RadioDroidBrowser(private val radioDroidApp: RadioDroidApp) {

    companion object {
        private const val TAG = "RadioDroidBrowser"
        private const val MEDIA_ID_ROOT = "__ROOT__"
        private const val MEDIA_ID_MUSICS_FAVORITE = "__FAVORITE__"
        private const val MEDIA_ID_MUSICS_HISTORY = "__HISTORY__"
        private const val MEDIA_ID_MUSICS_TOP = "__TOP__"
        private const val LEAF_SEPARATOR = '|'
        private const val IMAGE_LOAD_TIMEOUT_MS = 2000L

        @JvmStatic
        fun stationIdFromMediaId(mediaId: String?): String {
            if (mediaId == null) return ""
            val separatorIdx = mediaId.indexOf(LEAF_SEPARATOR)
            return if (separatorIdx <= 0) mediaId else mediaId.substring(separatorIdx + 1)
        }
    }

    private val stationIdToStation = mutableMapOf<String, DataRadioStation>()

    @Suppress("DEPRECATION")
    private inner class RetrieveStationsIconAndSendResult(
        private val result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>,
        private val stations: List<DataRadioStation>,
        context: Context
    ) : AsyncTask<Void, Void, Void>() {
        private val contextRef = WeakReference(context)
        private val stationIdToIcon = mutableMapOf<String, Bitmap>()
        private lateinit var countDownLatch: CountDownLatch
        private val resources = context.applicationContext.resources
        val imageLoadTargets = mutableListOf<Target>()

        override fun onPreExecute() {
            countDownLatch = CountDownLatch(stations.size)
            for (station in stations) {
                val ctx = contextRef.get() ?: break
                val target = object : Target {
                    override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom?) {
                        stationIdToIcon[station.StationUuid] = bitmap
                        countDownLatch.countDown()
                    }
                    override fun onBitmapFailed(e: Exception, errorDrawable: android.graphics.drawable.Drawable) {
                        onBitmapLoaded((errorDrawable as BitmapDrawable).bitmap, null)
                        countDownLatch.countDown()
                    }
                    override fun onPrepareLoad(placeHolderDrawable: android.graphics.drawable.Drawable?) {}
                }
                imageLoadTargets.add(target)
                Picasso.get()
                    .load(if (!station.hasIcon()) resourceToUri(resources, R.drawable.ic_launcher).toString() else station.IconUrl)
                    .transform(CropSquareTransformation())
                    .error(R.drawable.ic_launcher)
                    .transform(if (Utils.useCircularIcons(ctx)) CropCircleTransformation() else CropSquareTransformation())
                    .transform(RoundedCornersTransformation(12, 2, RoundedCornersTransformation.CornerType.ALL))
                    .resize(128, 128)
                    .into(target)
            }
        }

        override fun doInBackground(vararg voids: Void?): Void? {
            try { countDownLatch.await(IMAGE_LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
            catch (e: InterruptedException) { e.printStackTrace() }
            return null
        }

        override fun onPostExecute(v: Void?) {
            val context = contextRef.get()
            if (context != null) imageLoadTargets.forEach { Picasso.get().cancelRequest(it) }

            val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()
            for (station in stations) {
                var stationIcon = stationIdToIcon[station.StationUuid]
                    ?: BitmapFactory.decodeResource(android.content.res.Resources.getSystem(), R.drawable.ic_launcher)

                val extras = Bundle().apply {
                    putParcelable(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, stationIcon)
                    putParcelable(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, stationIcon)
                }
                val mediaItemBuilder = MediaDescriptionCompat.Builder()
                    .setMediaId(MEDIA_ID_MUSICS_HISTORY + LEAF_SEPARATOR + station.StationUuid)
                    .setTitle(station.Name)
                    .setDescription("${station.Country} ${station.Country} ${station.TagsAll}")
                    .setExtras(extras)

                if (!station.IconUrl.isNullOrEmpty()) {
                    val iconUrl = station.IconUrl.replace("http:", "https:")
                    mediaItemBuilder.setIconUri(Uri.parse(iconUrl))
                } else {
                    mediaItemBuilder.setIconUri(resourceToUri(resources, R.drawable.ic_photo_24dp))
                }
                mediaItems.add(MediaBrowserCompat.MediaItem(mediaItemBuilder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            }
            result.sendResult(mediaItems)
        }
    }

    fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(radioDroidApp.applicationContext)
        val extras = Bundle().apply {
            putInt(DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            if (sharedPref.getBoolean("load_icons", false) && sharedPref.getBoolean("icons_only_favorites_style", false)) {
                Log.d(TAG, "Setting grid style for playables")
                putInt(DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
            } else {
                Log.d(TAG, "Setting list style for playables")
                putInt(DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            }
        }
        return MediaBrowserServiceCompat.BrowserRoot(MEDIA_ID_ROOT, extras)
    }

    @Suppress("DEPRECATION")
    fun onLoadChildren(parentId: String, result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>) {
        val resources = radioDroidApp.resources
        if (parentId == MEDIA_ID_ROOT) {
            result.sendResult(createBrowsableMediaItemsForRoot())
            return
        }

        val stations: List<DataRadioStation>? = when (parentId) {
            MEDIA_ID_MUSICS_FAVORITE -> radioDroidApp.favouriteManager.getList()
            MEDIA_ID_MUSICS_HISTORY -> radioDroidApp.historyManager.getList()
            else -> null
        }

        if (!stations.isNullOrEmpty()) {
            stationIdToStation.clear()
            stations.forEach { stationIdToStation[it.StationUuid] = it }
            result.detach()
            RetrieveStationsIconAndSendResult(result, stations, radioDroidApp).execute()
        } else {
            result.sendResult(emptyList())
        }
    }

    fun getStationById(stationId: String): DataRadioStation? = stationIdToStation[stationId]

    private fun createBrowsableMediaItemsForRoot(): List<MediaBrowserCompat.MediaItem> {
        val resources = radioDroidApp.resources
        return listOf(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(MEDIA_ID_MUSICS_FAVORITE)
                    .setTitle(resources.getString(R.string.nav_item_starred))
                    .setIconUri(resourceToUri(resources, R.drawable.ic_star_white_24))
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            ),
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(MEDIA_ID_MUSICS_HISTORY)
                    .setTitle(resources.getString(R.string.nav_item_history))
                    .setIconUri(resourceToUri(resources, R.drawable.ic_star_white_24))
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )
    }
}
