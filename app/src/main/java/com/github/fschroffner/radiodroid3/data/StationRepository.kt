package com.github.fschroffner.radiodroid3.data

import android.content.Context
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.data.mapper.toDataRadioStation
import com.github.fschroffner.radiodroid3.data.mapper.toDomain
import com.github.fschroffner.radiodroid3.data.remote.dto.StationDtoParser
import com.github.fschroffner.radiodroid3.domain.model.RadioStation
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single point of access for radio-browser station lists.
 *
 * Encapsulates the network request against the radio-browser API with multi-server failover,
 * disk caching, and type-safe DTO-to-Domain mapping.
 */
@Singleton
class StationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {
    /**
     * Loads a station list for a radio-browser relative endpoint as immutable Domain Models.
     */
    suspend fun getDomainStations(
        relativeUrl: String,
        hideBroken: Boolean,
        forceUpdate: Boolean,
    ): List<RadioStation>? = withContext(Dispatchers.IO) {
        val params = mapOf("hidebroken" to hideBroken.toString())
        val json = Utils.downloadFeedRelative(httpClient, context, relativeUrl, forceUpdate, params)
            ?: return@withContext null
        StationDtoParser.parseList(json).map { it.toDomain() }
    }

    /**
     * Loads a station list for backward compatibility with existing views.
     */
    suspend fun getStations(
        relativeUrl: String,
        hideBroken: Boolean,
        forceUpdate: Boolean,
    ): List<DataRadioStation>? = withContext(Dispatchers.IO) {
        getDomainStations(relativeUrl, hideBroken, forceUpdate)?.map { it.toDataRadioStation() }
    }
}
