package com.github.fschroffner.radiodroid3.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Database Entity for local station persistence in `radio_stations`.
 *
 * Uses idiomatic Kotlin camelCase property names while maintaining exact
 * ColumnInfo names to preserve full SQLite schema compatibility.
 */
@Entity(tableName = "radio_stations")
data class RadioStationEntity(
    @PrimaryKey
    @ColumnInfo(name = "StationUuid")
    val stationUuid: String,

    @ColumnInfo(name = "Name")
    val name: String = "",

    @ColumnInfo(name = "ChangeUuid")
    val changeUuid: String = "",

    @ColumnInfo(name = "StreamUrl")
    val streamUrl: String = "",

    @ColumnInfo(name = "HomePageUrl")
    val homePageUrl: String = "",

    @ColumnInfo(name = "IconUrl")
    val iconUrl: String = "",

    @ColumnInfo(name = "Country")
    val country: String = "",

    @ColumnInfo(name = "CountryCode")
    val countryCode: String? = null,

    @ColumnInfo(name = "State")
    val state: String = "",

    @ColumnInfo(name = "TagsAll")
    val tagsAll: String = "",

    @ColumnInfo(name = "Language")
    val language: String = "",

    @ColumnInfo(name = "ClickCount")
    val clickCount: Int = 0,

    @ColumnInfo(name = "ClickTrend")
    val clickTrend: Int = 0,

    @ColumnInfo(name = "Votes")
    val votes: Int = 0,

    @ColumnInfo(name = "RefreshRetryCount")
    val refreshRetryCount: Int = 0,

    @ColumnInfo(name = "Bitrate")
    val bitrate: Int = 0,

    @ColumnInfo(name = "Codec")
    val codec: String? = null,

    @ColumnInfo(name = "Working")
    val working: Boolean = true,

    @ColumnInfo(name = "Hls")
    val hls: Boolean = false,

    @ColumnInfo(name = "DeletedOnServer")
    val deletedOnServer: Boolean = false,

    @ColumnInfo(name = "isFavorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "favoriteOrder")
    val favoriteOrder: Int = 0,

    @ColumnInfo(name = "lastPlayed")
    val lastPlayed: Long? = null
)
