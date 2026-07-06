package com.github.fschroffner.radiodroid3.station.live.metadata.lastfm.data

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class Toptags(
    @SerializedName("tag") @Expose var tag: List<Tag>? = null
)
