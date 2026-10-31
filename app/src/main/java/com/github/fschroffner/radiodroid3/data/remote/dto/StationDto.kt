package com.github.fschroffner.radiodroid3.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Object representing the raw JSON payload from the RadioBrowser API.
 * Uses SerializedName annotations to map API keys to idiomatic Kotlin camelCase properties.
 */
data class StationDto(
    @SerializedName("stationuuid") val stationUuid: String? = null,
    @SerializedName("id") val legacyId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("changeuuid") val changeUuid: String? = null,
    @SerializedName("url") val streamUrl: String? = null,
    @SerializedName("homepage") val homePageUrl: String? = null,
    @SerializedName("favicon") val iconUrl: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("countrycode") val countryCode: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("tags") val tags: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("clickcount") val clickCount: Int? = null,
    @SerializedName("clicktrend") val clickTrend: Int? = null,
    @SerializedName("votes") val votes: Int? = null,
    @SerializedName("refreshretrycount") val refreshRetryCount: Int? = null,
    @SerializedName("bitrate") val bitrate: Int? = null,
    @SerializedName("codec") val codec: String? = null,
    @SerializedName("lastcheckok") val lastCheckOk: Int? = null,
    @SerializedName("hls") val hls: Int? = null,
    @SerializedName("DeletedOnServer") val deletedOnServer: Int? = null,
    @SerializedName("isFavorite") val isFavorite: Boolean? = null,
    @SerializedName("favoriteOrder") val favoriteOrder: Int? = null,
    @SerializedName("lastPlayed") val lastPlayed: Long? = null
)
