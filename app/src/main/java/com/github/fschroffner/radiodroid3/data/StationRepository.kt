package com.github.fschroffner.radiodroid3.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single point of access for radio-browser station lists.
 *
 * Encapsulates the "where does the data come from" concern (network request against
 * the radio-browser API with multi-server failover, disk caching and JSON decoding)
 * behind a coroutine-friendly API. Callers no longer touch OkHttp, org.json or
 * threading directly.
 */
@Singleton
class StationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {
    /**
     * Loads a station list for a radio-browser relative endpoint
     * (e.g. `"json/stations/topvote/100"`).
     *
     * @param hideBroken   forwarded to the API as the `hidebroken` parameter.
     * @param forceUpdate  bypasses the on-disk cache when true.
     * @return the decoded stations, or `null` when the request failed on every server
     *         (so callers can keep showing the previous list).
     */
    suspend fun getStations(
        relativeUrl: String,
        hideBroken: Boolean,
        forceUpdate: Boolean,
    ): List<DataRadioStation>? = withContext(Dispatchers.IO) {
        val params = mapOf("hidebroken" to hideBroken.toString())
        val json = Utils.downloadFeedRelative(httpClient, context, relativeUrl, forceUpdate, params)
            ?: return@withContext null
        DataRadioStation.DecodeJson(json)
    }
}
