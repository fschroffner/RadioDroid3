package com.github.fschroffner.floradio.station.live.metadata.lastfm.data

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class Tag(
    @SerializedName("name") @Expose var name: String? = null,
    @SerializedName("url") @Expose var url: String? = null
)
