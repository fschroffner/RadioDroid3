package net.programmierecke.radiodroid2.station.live.metadata

import net.programmierecke.radiodroid2.station.live.metadata.lastfm.LfmMetadataSearcher
import okhttp3.OkHttpClient

class TrackMetadataSearcher(httpClient: OkHttpClient) {
    private val lfmMetadataSearcher = LfmMetadataSearcher(httpClient)

    fun fetchTrackMetadata(lastFMApiKey: String, artist: String, track: String, callback: TrackMetadataCallback) {
        lfmMetadataSearcher.fetchTrackMetadata(lastFMApiKey, artist, track, callback)
    }
}
