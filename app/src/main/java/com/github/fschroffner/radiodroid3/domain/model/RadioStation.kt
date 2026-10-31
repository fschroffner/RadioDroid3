package com.github.fschroffner.radiodroid3.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Immutable Domain Model representing a radio station.
 *
 * Adheres strictly to Kotlin camelCase naming conventions and clean architecture principles.
 * Completely decoupled from API serialization formats and database persistence concerns.
 */
@Parcelize
data class RadioStation(
    val stationUuid: String,
    val name: String = "",
    val streamUrl: String = "",
    val changeUuid: String = "",
    val homePageUrl: String = "",
    val iconUrl: String = "",
    val country: String = "",
    val countryCode: String? = null,
    val state: String = "",
    val tagsAll: String = "",
    val language: String = "",
    val clickCount: Int = 0,
    val clickTrend: Int = 0,
    val votes: Int = 0,
    val refreshRetryCount: Int = 0,
    val bitrate: Int = 0,
    val codec: String? = null,
    val working: Boolean = true,
    val hls: Boolean = false,
    val deletedOnServer: Boolean = false,
    val isFavorite: Boolean = false,
    val favoriteOrder: Int = 0,
    val lastPlayed: Long? = null,
    val playableUrl: String? = null
) : Parcelable {

    val hasIcon: Boolean get() = iconUrl.isNotBlank()
    val hasValidUuid: Boolean get() = stationUuid.isNotBlank()

    fun withPlayableUrl(url: String?): RadioStation = copy(playableUrl = url)
    fun withFavorite(favorite: Boolean, order: Int = 0): RadioStation = copy(isFavorite = favorite, favoriteOrder = order)
    fun withLastPlayed(timestamp: Long?): RadioStation = copy(lastPlayed = timestamp)
}
