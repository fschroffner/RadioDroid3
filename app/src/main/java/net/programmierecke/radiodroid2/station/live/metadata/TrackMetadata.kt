package net.programmierecke.radiodroid2.station.live.metadata

class TrackMetadata {
    enum class AlbumArtSize { SMALL, MEDIUM, LARGE, EXTRA_LARGE }

    data class AlbumArt(val size: AlbumArtSize, val url: String)

    var artist: String? = null
    var album: String? = null
    var track: String? = null
    var tags: ArrayList<String>? = null
    var albumArts: List<AlbumArt>? = null
}
