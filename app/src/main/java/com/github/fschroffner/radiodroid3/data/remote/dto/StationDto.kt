package com.github.fschroffner.radiodroid3.data.remote.dto

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object representing the raw JSON payload from the RadioBrowser API.
 * Supports both Kotlinx Serialization and Gson annotations simultaneously.
 */
@Serializable
data class StationDto(
    @SerialName("stationuuid") @SerializedName("stationuuid") val stationUuid: String? = null,
    @SerialName("id") @SerializedName("id") val legacyId: String? = null,
    @SerialName("name") @SerializedName("name") val name: String? = null,
    @SerialName("changeuuid") @SerializedName("changeuuid") val changeUuid: String? = null,
    @SerialName("url") @SerializedName("url") val streamUrl: String? = null,
    @SerialName("homepage") @SerializedName("homepage") val homePageUrl: String? = null,
    @SerialName("favicon") @SerializedName("favicon") val iconUrl: String? = null,
    @SerialName("country") @SerializedName("country") val country: String? = null,
    @SerialName("countrycode") @SerializedName("countrycode") val countryCode: String? = null,
    @SerialName("state") @SerializedName("state") val state: String? = null,
    @SerialName("tags") @SerializedName("tags") val tags: String? = null,
    @SerialName("language") @SerializedName("language") val language: String? = null,
    @SerialName("clickcount") @SerializedName("clickcount") val clickCount: Int? = null,
    @SerialName("clicktrend") @SerializedName("clicktrend") val clickTrend: Int? = null,
    @SerialName("votes") @SerializedName("votes") val votes: Int? = null,
    @SerialName("refreshretrycount") @SerializedName("refreshretrycount") val refreshRetryCount: Int? = null,
    @SerialName("bitrate") @SerializedName("bitrate") val bitrate: Int? = null,
    @SerialName("codec") @SerializedName("codec") val codec: String? = null,
    @SerialName("lastcheckok") @SerializedName("lastcheckok") val lastCheckOk: Int? = null,
    @SerialName("hls") @SerializedName("hls") val hls: Int? = null,
    @SerialName("DeletedOnServer") @SerializedName("DeletedOnServer") val deletedOnServer: Int? = null,
    @SerialName("isFavorite") @SerializedName("isFavorite") val isFavorite: Boolean? = null,
    @SerialName("favoriteOrder") @SerializedName("favoriteOrder") val favoriteOrder: Int? = null,
    @SerialName("lastPlayed") @SerializedName("lastPlayed") val lastPlayed: Long? = null
)
