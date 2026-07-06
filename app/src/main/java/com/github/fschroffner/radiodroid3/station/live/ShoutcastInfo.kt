package com.github.fschroffner.radiodroid3.station.live

import android.os.Parcel
import android.os.Parcelable
import androidx.media3.extractor.metadata.icy.IcyHeaders
import com.github.fschroffner.radiodroid3.Utils.parseIntWithDefault
import okhttp3.Response

class ShoutcastInfo() : Parcelable {
    var metadataOffset: Int = 0
    var bitrate: Int = 0
    var audioInfo: String? = null
    var audioDesc: String? = null
    var audioGenre: String? = null
    var audioName: String? = null
    var audioHomePage: String? = null
    var serverName: String? = null
    var serverPublic: Boolean = false
    var channels: Int = 0
    var sampleRate: Int = 0

    constructor(icyHeaders: IcyHeaders) : this() {
        bitrate = icyHeaders.bitrate
        audioGenre = icyHeaders.genre
        serverPublic = icyHeaders.isPublic
        audioName = icyHeaders.name
        audioHomePage = icyHeaders.url
    }

    private constructor(parcel: Parcel) : this() {
        metadataOffset = parcel.readInt()
        bitrate = parcel.readInt()
        audioInfo = parcel.readString()
        audioDesc = parcel.readString()
        audioGenre = parcel.readString()
        audioName = parcel.readString()
        audioHomePage = parcel.readString()
        serverName = parcel.readString()
        serverPublic = parcel.readByte() != 0.toByte()
        channels = parcel.readInt()
        sampleRate = parcel.readInt()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(metadataOffset); dest.writeInt(bitrate); dest.writeString(audioInfo)
        dest.writeString(audioDesc); dest.writeString(audioGenre); dest.writeString(audioName)
        dest.writeString(audioHomePage); dest.writeString(serverName)
        dest.writeByte(if (serverPublic) 1 else 0); dest.writeInt(channels); dest.writeInt(sampleRate)
    }

    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<ShoutcastInfo> {
        override fun createFromParcel(parcel: Parcel) = ShoutcastInfo(parcel)
        override fun newArray(size: Int): Array<ShoutcastInfo?> = arrayOfNulls(size)

        @JvmStatic
        fun Decode(response: Response): ShoutcastInfo? {
            val info = ShoutcastInfo().apply {
                metadataOffset = parseIntWithDefault(response.header("icy-metaint"), 0)
                bitrate = parseIntWithDefault(response.header("icy-br"), 0)
                audioInfo = response.header("ice-audio-info")
                audioDesc = response.header("icy-description")
                audioGenre = response.header("icy-genre")
                audioName = response.header("icy-name")
                audioHomePage = response.header("icy-url")
                serverName = response.header("Server")
                serverPublic = parseIntWithDefault(response.header("icy-pub"), 0) > 0
            }

            info.audioInfo?.let { ai ->
                val params = ai.split(";").mapNotNull { p -> val i = p.indexOf('='); if (i >= 0) p.substring(0, i) to p.substring(i + 1) else null }.toMap()
                info.channels = parseIntWithDefault(params["ice-channels"], 0).takeIf { it != 0 } ?: parseIntWithDefault(params["channels"], 0)
                info.sampleRate = parseIntWithDefault(params["ice-samplerate"], 0).takeIf { it != 0 } ?: parseIntWithDefault(params["samplerate"], 0)
                if (info.bitrate == 0) info.bitrate = parseIntWithDefault(params["ice-bitrate"], 0).takeIf { it != 0 } ?: parseIntWithDefault(params["bitrate"], 0)
            }

            return if (info.metadataOffset == 0) null else info
        }
    }
}
