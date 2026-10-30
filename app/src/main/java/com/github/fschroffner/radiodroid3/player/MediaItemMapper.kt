package com.github.fschroffner.radiodroid3.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.github.fschroffner.radiodroid3.station.DataRadioStation

object MediaItemMapper {
    const val EXTRA_STATION_UUID = "station_uuid"
    const val EXTRA_STATION_NAME = "station_name"
    const val EXTRA_STREAM_URL = "stream_url"
    const val EXTRA_PLAYABLE_URL = "playable_url"
    const val EXTRA_HOMEPAGE_URL = "homepage_url"
    const val EXTRA_ICON_URL = "icon_url"
    const val EXTRA_COUNTRY = "country"
    const val EXTRA_COUNTRY_CODE = "country_code"
    const val EXTRA_STATE = "state"
    const val EXTRA_TAGS_ALL = "tags_all"
    const val EXTRA_LANGUAGE = "language"
    const val EXTRA_BITRATE = "bitrate"
    const val EXTRA_CODEC = "codec"
    const val EXTRA_HLS = "hls"
    const val EXTRA_PLAY_IMMEDIATELY = "play_immediately"

    fun toMediaItem(
        station: DataRadioStation,
        playableUrl: String? = null,
        playImmediately: Boolean = true
    ): MediaItem {
        val effectivePlayableUrl = playableUrl ?: station.playableUrl
        val streamUri = effectivePlayableUrl ?: station.StreamUrl

        val extras = Bundle().apply {
            putString(EXTRA_STATION_UUID, station.StationUuid)
            putString(EXTRA_STATION_NAME, station.Name)
            putString(EXTRA_STREAM_URL, station.StreamUrl)
            if (effectivePlayableUrl != null) {
                putString(EXTRA_PLAYABLE_URL, effectivePlayableUrl)
            }
            putString(EXTRA_HOMEPAGE_URL, station.HomePageUrl)
            putString(EXTRA_ICON_URL, station.IconUrl)
            putString(EXTRA_COUNTRY, station.Country)
            station.CountryCode?.let { putString(EXTRA_COUNTRY_CODE, it) }
            putString(EXTRA_STATE, station.State)
            putString(EXTRA_TAGS_ALL, station.TagsAll)
            putString(EXTRA_LANGUAGE, station.Language)
            putInt(EXTRA_BITRATE, station.Bitrate)
            station.Codec?.let { putString(EXTRA_CODEC, it) }
            putBoolean(EXTRA_HLS, station.Hls)
            putBoolean(EXTRA_PLAY_IMMEDIATELY, playImmediately)
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(station.Name)
            .setArtist(station.TagsAll)
            .setStation(station.Name)
            .setSubtitle(station.Country)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .setExtras(extras)

        if (station.hasIcon()) {
            try {
                metadataBuilder.setArtworkUri(Uri.parse(station.IconUrl.replace("http:", "https:")))
            } catch (_: Exception) {
            }
        }

        val mediaItemBuilder = MediaItem.Builder()
            .setMediaId(station.StationUuid)
            .setMediaMetadata(metadataBuilder.build())

        if (streamUri.isNotBlank()) {
            try {
                val parsedUri = Uri.parse(streamUri)
                mediaItemBuilder.setUri(parsedUri)
                mediaItemBuilder.setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setMediaUri(parsedUri)
                        .setSearchQuery(station.Name)
                        .setExtras(extras)
                        .build()
                )
            } catch (_: Exception) {
            }
        }

        return mediaItemBuilder.build()
    }

    fun toDataRadioStation(mediaItem: MediaItem): DataRadioStation? {
        val extras = mediaItem.mediaMetadata.extras ?: mediaItem.requestMetadata?.extras
        val uuid = extras?.getString(EXTRA_STATION_UUID)
            ?: mediaItem.mediaId.takeIf { it.isNotBlank() && !it.startsWith("__") }
        if (uuid.isNullOrBlank()) return null

        return DataRadioStation().apply {
            StationUuid = uuid
            Name = extras?.getString(EXTRA_STATION_NAME) ?: mediaItem.mediaMetadata.title?.toString() ?: ""
            StreamUrl = extras?.getString(EXTRA_STREAM_URL)
                ?: mediaItem.requestMetadata?.mediaUri?.toString()
                ?: ""
            playableUrl = extras?.getString(EXTRA_PLAYABLE_URL)
            HomePageUrl = extras?.getString(EXTRA_HOMEPAGE_URL) ?: ""
            IconUrl = extras?.getString(EXTRA_ICON_URL)
                ?: mediaItem.mediaMetadata.artworkUri?.toString()
                ?: ""
            Country = extras?.getString(EXTRA_COUNTRY)
                ?: mediaItem.mediaMetadata.subtitle?.toString()
                ?: ""
            CountryCode = extras?.getString(EXTRA_COUNTRY_CODE)
            State = extras?.getString(EXTRA_STATE) ?: ""
            TagsAll = extras?.getString(EXTRA_TAGS_ALL)
                ?: mediaItem.mediaMetadata.artist?.toString()
                ?: ""
            Language = extras?.getString(EXTRA_LANGUAGE) ?: ""
            Bitrate = extras?.getInt(EXTRA_BITRATE, 0) ?: 0
            Codec = extras?.getString(EXTRA_CODEC)
            Hls = extras?.getBoolean(EXTRA_HLS, false) ?: false
        }
    }
}

fun DataRadioStation.toMediaItem(
    playableUrl: String? = null,
    playImmediately: Boolean = true
): MediaItem = MediaItemMapper.toMediaItem(this, playableUrl, playImmediately)

fun MediaItem.toDataRadioStation(): DataRadioStation? = MediaItemMapper.toDataRadioStation(this)
