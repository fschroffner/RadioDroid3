package com.github.fschroffner.radiodroid3.station.live.metadata

import com.github.fschroffner.radiodroid3.station.live.metadata.lastfm.LfmMetadataSearcher
import okhttp3.OkHttpClient

class TrackMetadataSearcher(httpClient: OkHttpClient) {
    private val lfmMetadataSearcher = LfmMetadataSearcher(httpClient)

    fun fetchTrackMetadata(lastFMApiKey: String, artist: String, track: String, callback: TrackMetadataCallback) {
        lfmMetadataSearcher.fetchTrackMetadata(lastFMApiKey, artist, track, callback)
    }
}
