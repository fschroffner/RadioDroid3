package com.github.fschroffner.radiodroid3.station.live.metadata.lastfm.data

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class Album(
    @SerializedName("artist") @Expose var artist: String? = null,
    @SerializedName("title") @Expose var title: String? = null,
    @SerializedName("url") @Expose var url: String? = null,
    @SerializedName("image") @Expose var image: List<Image>? = null
)
