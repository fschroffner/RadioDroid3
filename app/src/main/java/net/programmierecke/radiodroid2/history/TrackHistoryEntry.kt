package net.programmierecke.radiodroid2.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "track_history")
class TrackHistoryEntry {
    @PrimaryKey(autoGenerate = true)
    var uid: Int = 0

    @ColumnInfo(name = "station_uuid")
    var stationUuid: String = ""

    @ColumnInfo(name = "station_icon_url")
    var stationIconUrl: String = ""

    @ColumnInfo(name = "track")
    var track: String = ""

    @ColumnInfo(name = "artist")
    var artist: String = ""

    @ColumnInfo(name = "title")
    var title: String = ""

    @ColumnInfo(name = "art_url")
    var artUrl: String? = null

    @ColumnInfo(name = "start_time")
    var startTime: Date = Date()

    @ColumnInfo(name = "end_time")
    var endTime: Date = Date()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrackHistoryEntry) return false
        return uid == other.uid &&
            stationUuid == other.stationUuid &&
            track == other.track &&
            artist == other.artist &&
            title == other.title &&
            artUrl == other.artUrl &&
            startTime == other.startTime &&
            endTime == other.endTime
    }

    override fun hashCode(): Int {
        var result = uid
        result = 31 * result + stationUuid.hashCode()
        result = 31 * result + track.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (artUrl?.hashCode() ?: 0)
        result = 31 * result + startTime.hashCode()
        result = 31 * result + endTime.hashCode()
        return result
    }

    companion object {
        const val MAX_HISTORY_ITEMS_IN_TABLE = 1000
        const val MAX_UNKNOWN_TRACK_DURATION = 3 * 60 * 1000 // 3 minutes
    }
}
