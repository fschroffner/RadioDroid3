package com.github.fschroffner.radiodroid3.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.github.fschroffner.radiodroid3.FavouriteManager
import com.github.fschroffner.radiodroid3.HistoryManager
import com.github.fschroffner.radiodroid3.history.TrackHistoryRepository
import com.github.fschroffner.radiodroid3.service.PauseReason
import com.github.fschroffner.radiodroid3.players.PlayState
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.live.ShoutcastInfo
import com.github.fschroffner.radiodroid3.station.live.StreamLiveInfo
import com.github.fschroffner.radiodroid3.station.live.metadata.TrackMetadata
import com.github.fschroffner.radiodroid3.station.live.metadata.TrackMetadataCallback
import com.github.fschroffner.radiodroid3.station.live.metadata.TrackMetadataSearcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable UI State representing player components (small and full player).
 */
data class PlayerUiState(
    val station: DataRadioStation? = null,
    val isPlaying: Boolean = false,
    val playState: PlayState = PlayState.Idle,
    val isConnected: Boolean = false,
    val streamLiveInfo: StreamLiveInfo = StreamLiveInfo(emptyMap()),
    val shoutcastInfo: ShoutcastInfo? = null,
    val isFavorite: Boolean = false,
    val bufferedSeconds: Long = 0L,
    val transferredBytes: Long = 0L,
    val lastPlayStartTime: Long = 0L,
    val sleepTimerSeconds: Long = 0L,
    val albumArtUrl: String? = null
)

/**
 * Shared ViewModel for player controls and status with Unidirectional Data Flow (UDF).
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    val mediaControllerRepository: MediaControllerRepository,
    val favouriteManager: FavouriteManager,
    val historyManager: HistoryManager,
    val trackHistoryRepository: TrackHistoryRepository,
    val trackMetadataSearcher: TrackMetadataSearcher,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _albumArtUrl = MutableStateFlow<String?>(null)
    private var lastLiveInfo: StreamLiveInfo? = null
    private var firstPlayAttempted = false

    val uiState: StateFlow<PlayerUiState> = combine(
        mediaControllerRepository.playbackState,
        favouriteManager.stationsFlow,
        _albumArtUrl
    ) { playbackState, favorites, artUrl ->
        val currentStation = playbackState.station ?: historyManager.getFirst()
        val favList = if (favorites.isNotEmpty()) favorites else favouriteManager.listStations
        val isFav = currentStation != null && (favList.any { it.StationUuid == currentStation.StationUuid } || favouriteManager.has(currentStation.StationUuid))

        PlayerUiState(
            station = currentStation,
            isPlaying = playbackState.isPlaying,
            playState = playbackState.playState,
            isConnected = playbackState.isConnected,
            streamLiveInfo = playbackState.streamLiveInfo,
            shoutcastInfo = playbackState.shoutcastInfo,
            isFavorite = isFav,
            bufferedSeconds = playbackState.bufferedSeconds,
            transferredBytes = playbackState.transferredBytes,
            lastPlayStartTime = playbackState.lastPlayStartTime,
            sleepTimerSeconds = playbackState.sleepTimerSeconds,
            albumArtUrl = artUrl ?: currentStation?.IconUrl
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerUiState()
    )

    init {
        observePlaybackMetadata()
    }

    private fun observePlaybackMetadata() {
        viewModelScope.launch {
            mediaControllerRepository.playbackState.collect { state ->
                val liveInfo = state.streamLiveInfo
                val station = state.station ?: historyManager.getFirst()

                if (station == null) {
                    _albumArtUrl.value = null
                    return@collect
                }

                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val apiKey = prefs.getString("last_fm_api_key", "") ?: ""

                if (liveInfo.artist.isNullOrEmpty() || liveInfo.track.isNullOrEmpty() || apiKey.isEmpty()) {
                    _albumArtUrl.value = if (station.hasIcon()) station.IconUrl else null
                    return@collect
                }

                if (lastLiveInfo != null &&
                    lastLiveInfo?.artist == liveInfo.artist &&
                    lastLiveInfo?.track == liveInfo.track
                ) {
                    return@collect
                }

                lastLiveInfo = liveInfo

                trackHistoryRepository.getLastInsertedHistoryItem { entry, _ ->
                    if (entry != null && !entry.artUrl.isNullOrEmpty()) {
                        _albumArtUrl.value = entry.artUrl
                        return@getLastInsertedHistoryItem
                    }

                    trackMetadataSearcher.fetchTrackMetadata(
                        apiKey,
                        liveInfo.artist,
                        liveInfo.track,
                        object : TrackMetadataCallback {
                            override fun onSuccess(trackMetadata: TrackMetadata) {
                                val art = trackMetadata.albumArts?.firstOrNull()?.url
                                if (!art.isNullOrEmpty()) {
                                    _albumArtUrl.value = art
                                    if (entry != null && art != entry.stationIconUrl) {
                                        trackHistoryRepository.setTrackArtUrl(entry.uid, art)
                                    }
                                } else {
                                    _albumArtUrl.value = if (station.hasIcon()) station.IconUrl else null
                                }
                            }

                            override fun onFailure(failureType: TrackMetadataCallback.FailureType) {
                                _albumArtUrl.value = if (station.hasIcon()) station.IconUrl else null
                            }
                        }
                    )
                }
            }
        }
    }

    fun playPause(onRequirePlaySelection: (DataRadioStation) -> Unit) {
        if (mediaControllerRepository.isPlaying()) {
            mediaControllerRepository.pause(PauseReason.USER)
        } else {
            val station = getCurrentOrLastStation()
            if (station != null) {
                onRequirePlaySelection(station)
            }
        }
    }

    fun pause() {
        mediaControllerRepository.pause(PauseReason.USER)
    }

    fun skipToPrevious() {
        mediaControllerRepository.skipToPrevious()
    }

    fun skipToNext() {
        mediaControllerRepository.skipToNext()
    }

    fun toggleFavorite(currentStation: DataRadioStation? = null) {
        val station = currentStation ?: getCurrentOrLastStation() ?: return
        if (favouriteManager.has(station.StationUuid)) {
            favouriteManager.remove(station.StationUuid)
        } else {
            favouriteManager.add(station)
        }
    }

    fun getCurrentOrLastStation(): DataRadioStation? {
        return mediaControllerRepository.getCurrentStation() ?: historyManager.getFirst()
    }

    fun tryPlayAtStart(autoPlay: Boolean, autoOff: Boolean, timeoutMinutes: Int, onPlay: (DataRadioStation) -> Unit) {
        if (firstPlayAttempted || !mediaControllerRepository.isConnected()) return
        firstPlayAttempted = true
        if (!mediaControllerRepository.isPlaying() && autoPlay) {
            if (autoOff) {
                mediaControllerRepository.addTimer(timeoutMinutes * 60)
            }
            val station = getCurrentOrLastStation()
            if (station != null) {
                onPlay(station)
            }
        }
    }

    fun addTimer(seconds: Int) {
        mediaControllerRepository.addTimer(seconds)
    }

    fun clearTimer() {
        mediaControllerRepository.clearTimer()
    }
}
