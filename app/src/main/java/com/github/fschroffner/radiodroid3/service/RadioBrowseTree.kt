package com.github.fschroffner.radiodroid3.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import androidx.preference.PreferenceManager
import com.google.common.collect.ImmutableList
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.RadioDroidApp
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.station.DataRadioStation

/**
 * Media3 browse tree exposed by [PlayerService]'s [androidx.media3.session.MediaLibraryService.MediaLibrarySession].
 *
 * This replaces the former stand-alone `RadioDroidBrowser` / `RadioDroidBrowserService`
 * (MediaBrowserServiceCompat) pair: the browse hierarchy is now served directly by the playback
 * service, so a single Media3 service provides both playback and browsing to clients such as
 * Android Auto.
 *
 * The tree is a root folder containing two station folders (Favorites, History); each of those
 * lists the stored stations as playable leaves whose media id encodes the station UUID.
 */
class RadioBrowseTree(private val app: RadioDroidApp) {

    companion object {
        const val MEDIA_ID_ROOT = "__ROOT__"
        const val MEDIA_ID_FAVORITE = "__FAVORITE__"
        const val MEDIA_ID_HISTORY = "__HISTORY__"
        private const val LEAF_SEPARATOR = '|'

        /** Extracts the station UUID encoded in a playable leaf media id ("<folder>|<uuid>"). */
        fun stationIdFromMediaId(mediaId: String?): String {
            if (mediaId == null) return ""
            val separatorIdx = mediaId.indexOf(LEAF_SEPARATOR)
            return if (separatorIdx <= 0) mediaId else mediaId.substring(separatorIdx + 1)
        }
    }

    /** Browsable root of the tree. */
    fun rootItem(): MediaItem =
        browsableItem(MEDIA_ID_ROOT, "", null, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    /**
     * Content-style hints for Android Auto, mirroring the styling the legacy browser advertised:
     * browsable items as a list, playable items as a grid only when the favorites-icon style is on.
     */
    fun rootExtras(): Bundle {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(app.applicationContext)
        val gridForPlayables = sharedPref.getBoolean("load_icons", false) &&
                sharedPref.getBoolean("icons_only_favorites_style", false)
        return Bundle().apply {
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                if (gridForPlayables) MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                else MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
        }
    }

    /** Children of [parentId], or an empty list for unknown / leaf ids. */
    fun childrenOf(parentId: String): ImmutableList<MediaItem> = when (parentId) {
        MEDIA_ID_ROOT -> ImmutableList.of(
            browsableItem(
                MEDIA_ID_FAVORITE,
                app.getString(R.string.nav_item_starred),
                R.drawable.ic_star_white_24,
                MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS
            ),
            browsableItem(
                MEDIA_ID_HISTORY,
                app.getString(R.string.nav_item_history),
                R.drawable.ic_star_white_24,
                MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS
            )
        )
        MEDIA_ID_FAVORITE -> stationsToItems(MEDIA_ID_FAVORITE, app.favouriteManager.getList())
        MEDIA_ID_HISTORY -> stationsToItems(MEDIA_ID_HISTORY, app.historyManager.getList())
        else -> ImmutableList.of()
    }

    /** Looks up the browse item for a media id, whether it is a folder or a station leaf. */
    fun itemForMediaId(mediaId: String): MediaItem? {
        when (mediaId) {
            MEDIA_ID_ROOT -> return rootItem()
            MEDIA_ID_FAVORITE, MEDIA_ID_HISTORY -> return childrenOf(MEDIA_ID_ROOT)
                .firstOrNull { it.mediaId == mediaId }
        }
        val station = stationForMediaId(mediaId) ?: return null
        return stationItem(mediaId.substringBefore(LEAF_SEPARATOR), station)
    }

    /** Resolves a playable leaf media id back to the stored station it refers to. */
    fun stationForMediaId(mediaId: String): DataRadioStation? {
        val uuid = stationIdFromMediaId(mediaId)
        if (uuid.isEmpty()) return null
        return app.favouriteManager.getById(uuid) ?: app.historyManager.getById(uuid)
    }

    private fun stationsToItems(
        parentId: String,
        stations: List<DataRadioStation>?
    ): ImmutableList<MediaItem> {
        if (stations.isNullOrEmpty()) return ImmutableList.of()
        val builder = ImmutableList.builder<MediaItem>()
        for (station in stations) {
            builder.add(stationItem(parentId, station))
        }
        return builder.build()
    }

    private fun stationItem(parentId: String, station: DataRadioStation): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(station.Name)
            .setSubtitle(station.Country)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
        if (!station.IconUrl.isNullOrEmpty()) {
            metadataBuilder.setArtworkUri(Uri.parse(station.IconUrl.replace("http:", "https:")))
        }
        return MediaItem.Builder()
            .setMediaId(parentId + LEAF_SEPARATOR + station.StationUuid)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun browsableItem(
        mediaId: String,
        title: String,
        iconResId: Int?,
        mediaType: Int
    ): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
        iconResId?.let { metadataBuilder.setArtworkUri(Utils.resourceToUri(app.resources, it)) }
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }
}
