package com.github.fschroffner.radiodroid3.service

import com.github.fschroffner.radiodroid3.history.TrackHistoryEntry
import com.github.fschroffner.radiodroid3.history.TrackHistoryRepository
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.live.StreamLiveInfo
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
