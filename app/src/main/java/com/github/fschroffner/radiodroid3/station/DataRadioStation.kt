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
 * Preserves legacy PascalCase properties for existing views and adapters while
 * delegating JSON parsing to [StationDtoParser] (Gson DTOs) and database storage to
 * [com.github.fschroffner.radiodroid3.data.local.entity.RadioStationEntity].
 * For modern, immutable domain logic, use [com.github.fschroffner.radiodroid3.domain.model.RadioStation].
 */
@Parcelize
class DataRadioStation() : Parcelable {

    var StationUuid: String = ""
    var Name: String = ""
    var ChangeUuid: String = ""
    var StreamUrl: String = ""
    var HomePageUrl: String = ""
    var IconUrl: String = ""
    var Country: String = ""
    var CountryCode: String? = null
    var State: String = ""
    var TagsAll: String = ""
    var Language: String = ""
    var ClickCount: Int = 0
    var ClickTrend: Int = 0
    var Votes: Int = 0
    var RefreshRetryCount: Int = 0
    var Bitrate: Int = 0
    var Codec: String? = null
    var Working: Boolean = true
    var Hls: Boolean = false
    var DeletedOnServer: Boolean = false
    var isFavorite: Boolean = false
    var favoriteOrder: Int = 0
    var lastPlayed: Long? = null

    @IgnoredOnParcel
    var playableUrl: String? = null

    @Deprecated("Use StationUuid instead")
    @IgnoredOnParcel
    var StationId: String = ""

    fun hasIcon(): Boolean = !TextUtils.isEmpty(IconUrl)

    fun fixStationFields() {
        if (IconUrl.isBlank()) IconUrl = ""
    }

    fun hasValidUuid(): Boolean = !TextUtils.isEmpty(StationUuid)

    fun copyPropertiesFrom(station: DataRadioStation) {
        StationUuid = station.StationUuid
        @Suppress("DEPRECATION") StationId = station.StationId
        ChangeUuid = station.ChangeUuid
        Name = station.Name
        HomePageUrl = station.HomePageUrl
        StreamUrl = station.StreamUrl
        IconUrl = station.IconUrl
        Country = station.Country
        CountryCode = station.CountryCode
        State = station.State
        TagsAll = station.TagsAll
        Language = station.Language
        ClickCount = station.ClickCount
        ClickTrend = station.ClickTrend
        Votes = station.Votes
        RefreshRetryCount = station.RefreshRetryCount
        Bitrate = station.Bitrate
        Codec = station.Codec
        Working = station.Working
        isFavorite = station.isFavorite
        favoriteOrder = station.favoriteOrder
        lastPlayed = station.lastPlayed
        playableUrl = station.playableUrl
    }

    fun toJsonString(): String {
        return StationDtoParser.toJson(toDomain().toDto())
    }

    fun toJson(): JSONObject? {
        return try {
            JSONObject(toJsonString())
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
