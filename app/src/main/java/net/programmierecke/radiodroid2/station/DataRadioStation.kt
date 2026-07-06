package net.programmierecke.radiodroid2.station

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import android.util.Log
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import jp.wasabeef.picasso.transformations.CropCircleTransformation
import jp.wasabeef.picasso.transformations.CropSquareTransformation
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import net.programmierecke.radiodroid2.ActivityMain
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.StationSaveManager
import net.programmierecke.radiodroid2.Utils
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class DataRadioStation() : Parcelable {
    var Name: String = ""
    var StationUuid: String = ""
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
    var playableUrl: String? = null
    var queue: StationSaveManager? = null

    @Deprecated("Use StationUuid instead")
    var StationId: String = ""

    fun getShortDetails(ctx: Context): String {
        val list = mutableListOf<String>()
        if (DeletedOnServer) list.add(ctx.resources.getString(R.string.station_detail_deleted_on_server))
        if (!Working) list.add(ctx.resources.getString(R.string.station_detail_broken))
        if (Bitrate > 0) list.add(ctx.resources.getString(R.string.station_detail_bitrate, Bitrate))
        if (State.trim().isNotEmpty()) list.add(State)
        if (Language.trim().isNotEmpty()) list.add(Language)
        return TextUtils.join(", ", list)
    }

    fun getLongDetails(ctx: Context): String {
        val list = mutableListOf<String>()
        if (DeletedOnServer) list.add(ctx.resources.getString(R.string.station_detail_deleted_on_server))
        if (!Working) list.add(ctx.resources.getString(R.string.station_detail_broken))
        if (Bitrate > 0) list.add(ctx.resources.getString(R.string.station_detail_bitrate, Bitrate))
        if (!TextUtils.isEmpty(Codec)) list.add(Codec!!)
        if (State.trim().isNotEmpty()) list.add(State)
        if (Language.trim().isNotEmpty()) list.add(Language)
        return TextUtils.join(", ", list)
    }

    fun hasIcon(): Boolean = !TextUtils.isEmpty(IconUrl)

    private fun fixStationFields() {
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
    }

    fun refresh(httpClient: OkHttpClient, context: Context): Boolean {
        @Suppress("DEPRECATION")
        val refreshed = if (!TextUtils.isEmpty(StationUuid)) Utils.getStationByUuid(httpClient, context, StationUuid)
                        else Utils.getStationById(httpClient, context, StationId)
        return if (refreshed != null && refreshed.hasValidUuid()) {
            copyPropertiesFrom(refreshed)
            RefreshRetryCount = 0
            true
        } else {
            if (Utils.hasAnyConnection(context)) RefreshRetryCount++
            false
        }
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
            }
        } catch (e: JSONException) {
            Log.e(TAG, "toJson() $e")
            null
        }
    }

    fun prepareShortcut(ctx: Context, cb: ShortcutReadyListener) {
        Picasso.get()
            .load(if (!hasIcon()) Utils.resourceToUri(ctx.resources, R.drawable.ic_launcher).toString() else IconUrl)
            .error(R.drawable.ic_launcher)
            .transform(if (Utils.useCircularIcons(ctx)) CropCircleTransformation() else CropSquareTransformation())
            .transform(RoundedCornersTransformation(12, 2, RoundedCornersTransformation.CornerType.ALL))
            .into(RadioIconTarget(ctx, this, cb))
    }

    interface ShortcutReadyListener {
        fun onShortcutReadyListener(shortcutInfo: ShortcutInfo)
    }

    inner class RadioIconTarget(
        private val ctx: Context,
        private val station: DataRadioStation,
        private val cb: ShortcutReadyListener
    ) : Target {
        override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom?) {
            if (Build.VERSION.SDK_INT >= 25) {
                val intent = Intent(ActivityMain.ACTION_PLAY_STATION_BY_UUID, null, ctx, ActivityMain::class.java)
                    .putExtra(ActivityMain.EXTRA_STATION_UUID, station.StationUuid)
                val shortcut = ShortcutInfo.Builder(ctx.applicationContext, "${ctx.packageName}/${station.StationUuid}")
                    .setShortLabel(station.Name)
                    .setIcon(Icon.createWithBitmap(bitmap))
                    .setIntent(intent)
                    .build()
                cb.onShortcutReadyListener(shortcut)
            }
        }

        override fun onBitmapFailed(e: Exception, errorDrawable: Drawable) {
            onBitmapLoaded((errorDrawable as BitmapDrawable).bitmap, null)
        }

        override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(Name)
        dest.writeString(StationUuid)
        dest.writeString(ChangeUuid)
        dest.writeString(StreamUrl)
        dest.writeString(HomePageUrl)
        dest.writeString(IconUrl)
        dest.writeString(Country)
        dest.writeString(CountryCode)
        dest.writeString(State)
        dest.writeString(TagsAll)
        dest.writeString(Language)
        dest.writeInt(ClickCount)
        dest.writeInt(ClickTrend)
        dest.writeInt(Votes)
        dest.writeInt(Bitrate)
        dest.writeString(Codec)
        dest.writeByte(if (Working) 1 else 0)
        dest.writeByte(if (Hls) 1 else 0)
        dest.writeString(playableUrl)
        @Suppress("DEPRECATION") dest.writeString(StationId)
    }

    private constructor(parcel: Parcel) : this() {
        Name = parcel.readString() ?: ""
        StationUuid = parcel.readString() ?: ""
        ChangeUuid = parcel.readString() ?: ""
        StreamUrl = parcel.readString() ?: ""
        HomePageUrl = parcel.readString() ?: ""
        IconUrl = parcel.readString() ?: ""
        Country = parcel.readString() ?: ""
        CountryCode = parcel.readString()
        State = parcel.readString() ?: ""
        TagsAll = parcel.readString() ?: ""
        Language = parcel.readString() ?: ""
        ClickCount = parcel.readInt()
        ClickTrend = parcel.readInt()
        Votes = parcel.readInt()
        Bitrate = parcel.readInt()
        Codec = parcel.readString()
        Working = parcel.readByte() != 0.toByte()
        Hls = parcel.readByte() != 0.toByte()
        playableUrl = parcel.readString()
        @Suppress("DEPRECATION") StationId = parcel.readString() ?: ""
    }

    companion object {
        private const val TAG = "DATAStation"
        const val MAX_REFRESH_RETRIES = 16
        const val RADIO_STATION_LOCAL_INFO_CHAGED = "net.programmierecke.radiodroid2.radiostation.changed"
        const val RADIO_STATION_UUID = "UUID"

        @JvmField
        val CREATOR = object : Parcelable.Creator<DataRadioStation> {
            override fun createFromParcel(source: Parcel) = DataRadioStation(source)
            override fun newArray(size: Int) = arrayOfNulls<DataRadioStation>(size)
        }

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
            station.fixStationFields()
            return station
        }
    }
}
