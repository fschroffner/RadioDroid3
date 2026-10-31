package com.github.fschroffner.radiodroid3.station

import android.os.Parcelable
import android.text.TextUtils
import android.util.Log
import com.github.fschroffner.radiodroid3.data.mapper.toDataRadioStation
import com.github.fschroffner.radiodroid3.data.mapper.toDomain
import com.github.fschroffner.radiodroid3.data.mapper.toDto
import com.github.fschroffner.radiodroid3.data.remote.dto.StationDtoParser
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

/**
 * Radio station UI model and backward-compatibility bridge.
 *
 * Internally uses standardized Kotlin camelCase naming conventions.
 * JSON serialization delegates to [StationDtoParser] (Gson DTOs) instead of manual org.json loops.
 * Room persistence is completely decoupled and handled by [com.github.fschroffner.radiodroid3.data.local.entity.RadioStationEntity].
 */
@Parcelize
class DataRadioStation() : Parcelable {

    var stationUuid: String = ""
    var name: String = ""
    var changeUuid: String = ""
    var streamUrl: String = ""
    var homePageUrl: String = ""
    var iconUrl: String = ""
    var country: String = ""
    var countryCode: String? = null
    var state: String = ""
    var tagsAll: String = ""
    var language: String = ""
    var clickCount: Int = 0
    var clickTrend: Int = 0
    var votes: Int = 0
    var refreshRetryCount: Int = 0
    var bitrate: Int = 0
    var codec: String? = null
    var working: Boolean = true
    var hls: Boolean = false
    var deletedOnServer: Boolean = false
    var isFavorite: Boolean = false
    var favoriteOrder: Int = 0
    var lastPlayed: Long? = null

    @IgnoredOnParcel
    var playableUrl: String? = null

    @Deprecated("Use stationUuid instead")
    @IgnoredOnParcel
    var stationId: String = ""

    // Backward-compatibility PascalCase accessors
    @Deprecated("Use stationUuid instead", ReplaceWith("stationUuid"))
    var StationUuid: String
        get() = stationUuid
        set(value) { stationUuid = value }

    @Deprecated("Use name instead", ReplaceWith("name"))
    var Name: String
        get() = name
        set(value) { name = value }

    @Deprecated("Use changeUuid instead", ReplaceWith("changeUuid"))
    var ChangeUuid: String
        get() = changeUuid
        set(value) { changeUuid = value }

    @Deprecated("Use streamUrl instead", ReplaceWith("streamUrl"))
    var StreamUrl: String
        get() = streamUrl
        set(value) { streamUrl = value }

    @Deprecated("Use homePageUrl instead", ReplaceWith("homePageUrl"))
    var HomePageUrl: String
        get() = homePageUrl
        set(value) { homePageUrl = value }

    @Deprecated("Use iconUrl instead", ReplaceWith("iconUrl"))
    var IconUrl: String
        get() = iconUrl
        set(value) { iconUrl = value }

    @Deprecated("Use country instead", ReplaceWith("country"))
    var Country: String
        get() = country
        set(value) { country = value }

    @Deprecated("Use countryCode instead", ReplaceWith("countryCode"))
    var CountryCode: String?
        get() = countryCode
        set(value) { countryCode = value }

    @Deprecated("Use state instead", ReplaceWith("state"))
    var State: String
        get() = state
        set(value) { state = value }

    @Deprecated("Use tagsAll instead", ReplaceWith("tagsAll"))
    var TagsAll: String
        get() = tagsAll
        set(value) { tagsAll = value }

    @Deprecated("Use language instead", ReplaceWith("language"))
    var Language: String
        get() = language
        set(value) { language = value }

    @Deprecated("Use clickCount instead", ReplaceWith("clickCount"))
    var ClickCount: Int
        get() = clickCount
        set(value) { clickCount = value }

    @Deprecated("Use clickTrend instead", ReplaceWith("clickTrend"))
    var ClickTrend: Int
        get() = clickTrend
        set(value) { clickTrend = value }

    @Deprecated("Use votes instead", ReplaceWith("votes"))
    var Votes: Int
        get() = votes
        set(value) { votes = value }

    @Deprecated("Use refreshRetryCount instead", ReplaceWith("refreshRetryCount"))
    var RefreshRetryCount: Int
        get() = refreshRetryCount
        set(value) { refreshRetryCount = value }

    @Deprecated("Use bitrate instead", ReplaceWith("bitrate"))
    var Bitrate: Int
        get() = bitrate
        set(value) { bitrate = value }

    @Deprecated("Use codec instead", ReplaceWith("codec"))
    var Codec: String?
        get() = codec
        set(value) { codec = value }

    @Deprecated("Use working instead", ReplaceWith("working"))
    var Working: Boolean
        get() = working
        set(value) { working = value }

    @Deprecated("Use hls instead", ReplaceWith("hls"))
    var Hls: Boolean
        get() = hls
        set(value) { hls = value }

    @Deprecated("Use deletedOnServer instead", ReplaceWith("deletedOnServer"))
    var DeletedOnServer: Boolean
        get() = deletedOnServer
        set(value) { deletedOnServer = value }

    @Deprecated("Use stationId instead", ReplaceWith("stationId"))
    var StationId: String
        get() = stationId
        set(value) { stationId = value }

    fun hasIcon(): Boolean = !TextUtils.isEmpty(iconUrl)

    fun fixStationFields() {
        if (iconUrl.isBlank()) iconUrl = ""
    }

    fun hasValidUuid(): Boolean = !TextUtils.isEmpty(stationUuid)

    fun copyPropertiesFrom(station: DataRadioStation) {
        stationUuid = station.stationUuid
        @Suppress("DEPRECATION") stationId = station.stationId
        changeUuid = station.changeUuid
        name = station.name
        homePageUrl = station.homePageUrl
        streamUrl = station.streamUrl
        iconUrl = station.iconUrl
        country = station.country
        countryCode = station.countryCode
        state = station.state
        tagsAll = station.tagsAll
        language = station.language
        clickCount = station.clickCount
        clickTrend = station.clickTrend
        votes = station.votes
        refreshRetryCount = station.refreshRetryCount
        bitrate = station.bitrate
        codec = station.codec
        working = station.working
        isFavorite = station.isFavorite
        favoriteOrder = station.favoriteOrder
        lastPlayed = station.lastPlayed
        playableUrl = station.playableUrl
    }

    fun toJson(): JSONObject? {
        return try {
            val dto = toDomain().toDto()
            JSONObject(StationDtoParser.toJson(dto))
        } catch (e: Exception) {
            Log.e(TAG, "toJson() error: $e")
            null
        }
    }

    companion object {
        private const val TAG = "DATAStation"
        const val MAX_REFRESH_RETRIES = 16
        const val RADIO_STATION_LOCAL_INFO_CHAGED = "com.github.fschroffner.radiodroid3.radiostation.changed"
        const val RADIO_STATION_LOCAL_INFO_CHANGED = RADIO_STATION_LOCAL_INFO_CHAGED
        const val RADIO_STATION_UUID = "UUID"

        @JvmStatic
        fun DecodeJson(result: String?): List<DataRadioStation> {
            val dtos = StationDtoParser.parseList(result)
            return dtos.map { it.toDomain().toDataRadioStation() }
        }

        @JvmStatic
        fun DecodeJsonSingle(result: String?): DataRadioStation? {
            return StationDtoParser.parseSingle(result)?.toDomain()?.toDataRadioStation()
        }
    }
}
