package net.programmierecke.radiodroid2.service

import net.programmierecke.radiodroid2.history.TrackHistoryEntry
import net.programmierecke.radiodroid2.history.TrackHistoryRepository
import net.programmierecke.radiodroid2.station.DataRadioStation
import net.programmierecke.radiodroid2.station.live.StreamLiveInfo
import java.util.Calendar
import java.util.Date

/**
 * Persists the now-playing track into the play history whenever the live metadata title changes.
 * Extracted from [PlayerService.foundLiveStreamInfo] so the database bookkeeping is isolated from the
 * service's playback and notification handling.
 */
class TrackHistoryUpdater(private val repository: TrackHistoryRepository) {

    fun onTrackChanged(station: DataRadioStation, liveInfo: StreamLiveInfo) {
        val currentTime = Calendar.getInstance().time

        repository.getLastInsertedHistoryItem { trackHistoryEntry, dao ->
            if (trackHistoryEntry != null && trackHistoryEntry.title == liveInfo.title) {
                trackHistoryEntry.endTime = Date(0)
                dao.update(trackHistoryEntry)
            } else {
                dao.setCurrentPlayingTrackEndTime(currentTime)

                val newTrackHistoryEntry = TrackHistoryEntry().apply {
                    stationUuid = station.StationUuid
                    artist = liveInfo.artist
                    title = liveInfo.title
                    track = liveInfo.track
                    stationIconUrl = station.IconUrl
                    startTime = currentTime
                    endTime = Date(0)
                }
                repository.insert(newTrackHistoryEntry)
            }
        }
    }
}
