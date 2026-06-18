package net.programmierecke.radiodroid2.station.live.metadata.lastfm

import android.text.TextUtils
import com.google.gson.Gson
import net.programmierecke.radiodroid2.station.live.metadata.TrackMetadata
import net.programmierecke.radiodroid2.station.live.metadata.TrackMetadataCallback
import net.programmierecke.radiodroid2.station.live.metadata.lastfm.data.LfmTrackMetadata
import net.programmierecke.radiodroid2.utils.RateLimiter
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class LfmMetadataSearcher(private val httpClient: OkHttpClient) {
    private val gson = Gson()
    private val rateLimiter = RateLimiter(4, 60 * 1000)

    fun fetchTrackMetadata(lastFMApiKey: String, artist: String, track: String, callback: TrackMetadataCallback) {
        if (lastFMApiKey.isEmpty() || TextUtils.isEmpty(track)) {
            callback.onFailure(TrackMetadataCallback.FailureType.UNRECOVERABLE)
            return
        }
        if (rateLimiter.allowed()) {
            httpClient.newCall(buildRequest(lastFMApiKey, artist.trim(), track.trim()))
                .enqueue(MetadataCallback(callback, lastFMApiKey, artist.trim(), track.trim()))
        } else {
            callback.onFailure(TrackMetadataCallback.FailureType.RECOVERABLE)
        }
    }

    private fun tryNormalizeTrack(track: String): String? {
        val normalized = track.replace(Regex("\\(.*\\)"), "").replace(Regex("\\[.*\\]"), "").replace(Regex("\\*.*\\*"), "").trim()
        return if (normalized == track) null else normalized
    }

    private fun buildRequest(apiKey: String, artist: String, track: String): Request {
        val url = "$API_GET_TRACK_METADATA".format(apiKey, artist, track).toHttpUrlOrNull()!!
        return Request.Builder().url(url).get().build()
    }

    private inner class MetadataCallback(
        private val trackMetadataCallback: TrackMetadataCallback,
        private val lastFMApiKey: String,
        private val artist: String,
        private val track: String
    ) : Callback {
        override fun onFailure(call: Call, e: IOException) {
            trackMetadataCallback.onFailure(TrackMetadataCallback.FailureType.RECOVERABLE)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val lfmData = gson.fromJson(response.body!!.charStream(), LfmTrackMetadata::class.java)
                val trackData = lfmData.track
                if (trackData == null) {
                    val normalized = tryNormalizeTrack(track)
                    if (normalized != null && normalized.length > 3) {
                        httpClient.newCall(buildRequest(lastFMApiKey, artist, normalized))
                            .enqueue(MetadataCallback(trackMetadataCallback, lastFMApiKey, artist, normalized))
                    } else {
                        trackMetadataCallback.onFailure(TrackMetadataCallback.FailureType.UNRECOVERABLE)
                    }
                    return
                }

                val metadata = TrackMetadata()
                trackData.artist?.let { metadata.artist = it.name }

                val albumArts = mutableListOf<TrackMetadata.AlbumArt>()
                trackData.album?.let { album ->
                    metadata.album = album.title
                    album.image?.forEach { img ->
                        val size = when (img.size) {
                            "small" -> TrackMetadata.AlbumArtSize.SMALL
                            "medium" -> TrackMetadata.AlbumArtSize.MEDIUM
                            "large" -> TrackMetadata.AlbumArtSize.LARGE
                            "extralarge" -> TrackMetadata.AlbumArtSize.EXTRA_LARGE
                            else -> TrackMetadata.AlbumArtSize.SMALL
                        }
                        albumArts.add(TrackMetadata.AlbumArt(size, img.text ?: ""))
                    }
                    albumArts.sortByDescending { it.size }
                }
                metadata.albumArts = albumArts
                metadata.track = trackData.name

                trackMetadataCallback.onSuccess(metadata)
            } catch (_: Exception) {
                trackMetadataCallback.onFailure(TrackMetadataCallback.FailureType.UNRECOVERABLE)
            }
        }
    }

    companion object {
        private const val API_GET_TRACK_METADATA = "http://ws.audioscrobbler.com/2.0/?method=track.getInfo&api_key=%s&artist=%s&track=%s&format=json"
    }
}
