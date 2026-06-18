package net.programmierecke.radiodroid2.station.live.metadata.lastfm.data

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class Image(
    @SerializedName("#text") @Expose var text: String? = null,
    @SerializedName("size") @Expose var size: String? = null
)
