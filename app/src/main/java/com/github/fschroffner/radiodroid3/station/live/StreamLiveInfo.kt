package com.github.fschroffner.radiodroid3.station.live

import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils

class StreamLiveInfo : Parcelable {
    var title: String = ""
        private set
    var artist: String = ""
        private set
    var track: String = ""
        private set
    var rawMetadata: Map<String, String>? = null
        private set

    constructor(rawMetadata: Map<String, String>?) {
        this.rawMetadata = rawMetadata
        rawMetadata?.get("StreamTitle")?.let { t ->
            title = t
            if (!TextUtils.isEmpty(t)) {
                val parts = t.split(" - ", limit = 2)
                artist = parts[0]
                if (parts.size == 2) track = parts[1]
            }
        }
    }

    fun hasArtistAndTrack() = artist.isNotEmpty() && track.isNotEmpty()

    private constructor(parcel: Parcel) {
        title = parcel.readString() ?: ""
        artist = parcel.readString() ?: ""
        track = parcel.readString() ?: ""
        @Suppress("UNCHECKED_CAST")
        rawMetadata = parcel.readHashMap(String::class.java.classLoader) as? Map<String, String>
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(title); parcel.writeString(artist); parcel.writeString(track)
        parcel.writeMap(rawMetadata)
    }

    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<StreamLiveInfo> {
        override fun createFromParcel(parcel: Parcel) = StreamLiveInfo(parcel)
        override fun newArray(size: Int): Array<StreamLiveInfo?> = arrayOfNulls(size)
    }
}
