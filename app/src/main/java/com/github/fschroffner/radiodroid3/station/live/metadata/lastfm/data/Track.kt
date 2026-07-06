package com.github.fschroffner.radiodroid3.station.live.metadata.lastfm.data

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class Track(
    @SerializedName("name") @Expose var name: String? = null,
    @SerializedName("mbid") @Expose var mbid: String? = null,
    @SerializedName("url") @Expose var url: String? = null,
    @SerializedName("duration") @Expose var duration: String? = null,
    @SerializedName("streamable") @Expose var streamable: Streamable? = null,
    @SerializedName("listeners") @Expose var listeners: String? = null,
    @SerializedName("playcount") @Expose var playcount: String? = null,
    @SerializedName("artist") @Expose var artist: Artist? = null,
    @SerializedName("album") @Expose var album: Album? = null,
    @SerializedName("toptags") @Expose var toptags: Toptags? = null
)
