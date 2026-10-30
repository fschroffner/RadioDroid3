package com.github.fschroffner.radiodroid3.station

import android.os.Parcelable
import android.text.TextUtils
import android.util.Log
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

@Parcelize
@Entity(tableName = "radio_stations")
class DataRadioStation() : Parcelable {
    @PrimaryKey
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

    @Ignore
    @IgnoredOnParcel
    var playableUrl: String? = null

    @Deprecated("Use StationUuid instead")
    @Ignore
    @IgnoredOnParcel
    var StationId: String = ""

    fun hasIcon(): Boolean = !TextUtils.isEmpty(IconUrl)

    fun fixStationFields() {
        if (IconUrl.isNullOrBlank()) IconUrl = ""
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
    }

    fun toJson(): JSONObject? {
        return try {
            JSONObject().apply {
                @Suppress("DEPRECATION")
                if (TextUtils.isEmpty(StationUuid)) put("id", StationId) else put("stationuuid", StationUuid)
                put("changeuuid", ChangeUuid)
                put("name", Name)
                put("homepage", HomePageUrl)
                put("url", StreamUrl)
                put("favicon", IconUrl)
                put("country", Country)
                put("countrycode", CountryCode)
                put("state", State)
                put("tags", TagsAll)
                put("language", Language)
                put("clickcount", ClickCount)
                put("clicktrend", ClickTrend)
                if (RefreshRetryCount > 0) put("refreshretrycount", RefreshRetryCount)
                put("votes", Votes)
                put("bitrate", "$Bitrate")
                put("codec", Codec)
                put("lastcheckok", if (Working) "1" else "0")
                put("DeletedOnServer", if (DeletedOnServer) "1" else "0")
                put("isFavorite", if (isFavorite) "1" else "0")
                put("favoriteOrder", favoriteOrder)
                if (lastPlayed != null) put("lastPlayed", lastPlayed)
            }
        } catch (e: JSONException) {
            Log.e(TAG, "toJson() $e")
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
            val list = mutableListOf<DataRadioStation>()
            if (result != null && TextUtils.isGraphic(result)) {
                try {
                    val jsonArray = JSONArray(result)
                    for (i in 0 until jsonArray.length()) {
                        try {
                            list.add(decodeObject(jsonArray.getJSONObject(i)))
                        } catch (e: Exception) {
                            Log.e(TAG, "DecodeJson() #2 $e")
                        }
                    }
                } catch (e: JSONException) {
                    Log.e(TAG, "DecodeJson() #1 $e")
                }
            }
            return list
        }

        @JvmStatic
        fun DecodeJsonSingle(result: String?): DataRadioStation? {
            if (result != null && TextUtils.isGraphic(result)) {
                return try {
                    decodeObject(JSONObject(result))
                } catch (e: JSONException) {
                    Log.e(TAG, "DecodeJsonSingle() $e")
                    null
                }
            }
            return null
        }

        private fun decodeObject(obj: JSONObject): DataRadioStation {
            val station = DataRadioStation()
            station.Name = obj.getString("name")
            station.StreamUrl = if (obj.has("url")) obj.getString("url") else ""
            if (obj.has("stationuuid")) station.StationUuid = obj.getString("stationuuid")
            @Suppress("DEPRECATION")
            if (!station.hasValidUuid()) station.StationId = obj.optString("id", "")
            if (obj.has("changeuuid")) station.ChangeUuid = obj.getString("changeuuid")
            station.Votes = obj.getInt("votes")
            station.RefreshRetryCount = if (obj.has("refreshretrycount")) obj.getInt("refreshretrycount") else 0
            station.HomePageUrl = obj.getString("homepage")
            station.TagsAll = obj.getString("tags")
            station.Country = obj.getString("country")
            if (obj.has("countrycode")) station.CountryCode = obj.getString("countrycode")
            station.State = obj.getString("state")
            station.IconUrl = obj.getString("favicon")
            station.Language = obj.getString("language")
            station.ClickCount = obj.getInt("clickcount")
            if (obj.has("clicktrend")) station.ClickTrend = obj.getInt("clicktrend")
            if (obj.has("bitrate")) station.Bitrate = obj.getInt("bitrate")
            if (obj.has("codec")) station.Codec = obj.getString("codec")
            if (obj.has("lastcheckok")) station.Working = obj.getInt("lastcheckok") != 0
            if (obj.has("hls")) station.Hls = obj.getInt("hls") != 0
            if (obj.has("DeletedOnServer")) station.DeletedOnServer = obj.getInt("DeletedOnServer") != 0
            if (obj.has("isFavorite")) station.isFavorite = obj.optBoolean("isFavorite", false) || obj.optInt("isFavorite", 0) == 1
            if (obj.has("favoriteOrder")) station.favoriteOrder = obj.optInt("favoriteOrder", 0)
            if (obj.has("lastPlayed")) station.lastPlayed = obj.optLong("lastPlayed")
            station.fixStationFields()
            return station
        }
    }
}
