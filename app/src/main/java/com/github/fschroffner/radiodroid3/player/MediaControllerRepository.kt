package com.github.fschroffner.radiodroid3.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.github.fschroffner.radiodroid3.BuildConfig
import com.github.fschroffner.radiodroid3.players.PlayState
import com.github.fschroffner.radiodroid3.players.selector.PlayerType
import com.github.fschroffner.radiodroid3.service.PauseReason
import com.github.fschroffner.radiodroid3.service.PlayerService
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.live.ShoutcastInfo
import com.github.fschroffner.radiodroid3.station.live.StreamLiveInfo
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

interface RadioPlayerController {
    val uiState: StateFlow<PlayerBasicState>
    fun play()
    fun pause()
    fun skip()
}

data class PlayerBasicState(
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = ""
)

@Singleton
class MediaControllerRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : RadioPlayerController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _meteredConnectionEvents = MutableSharedFlow<PlayerType>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val meteredConnectionEvents: SharedFlow<PlayerType> = _meteredConnectionEvents.asSharedFlow()

    override val uiState: StateFlow<PlayerBasicState> = _playbackState.map { state ->
        val title = state.streamLiveInfo.title?.takeIf { it.isNotBlank() }
            ?: state.station?.Name
            ?: ""
        val artist = state.streamLiveInfo.artist?.takeIf { it.isNotBlank() }
            ?: state.station?.TagsAll
            ?: ""
        PlayerBasicState(
            isPlaying = state.isPlaying,
            title = title,
            artist = artist
        )
    }.stateIn(scope, SharingStarted.Eagerly, PlayerBasicState())

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val playState = when (playbackState) {
                Player.STATE_BUFFERING -> PlayState.PrePlaying
                Player.STATE_READY -> if (mediaController?.playWhenReady == true) PlayState.Playing else PlayState.Paused
                Player.STATE_IDLE, Player.STATE_ENDED -> PlayState.Idle
                else -> PlayState.Idle
            }
            _playbackState.update { state ->
                state.copy(
                    playState = playState,
                    isPlaying = mediaController?.isPlaying == true
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { state -> state.copy(isPlaying = isPlaying) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null) {
                val station = mediaItem.toDataRadioStation()
                if (station != null) {
                    _playbackState.update { it.copy(station = station) }
                }
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val title = mediaMetadata.title?.toString()
            val artist = mediaMetadata.artist?.toString()
            if (!title.isNullOrEmpty() || !artist.isNullOrEmpty()) {
                val currentLive = _playbackState.value.streamLiveInfo
                if (currentLive.title != title || currentLive.artist != artist) {
                    val streamTitle = if (!artist.isNullOrEmpty() && !title.isNullOrEmpty()) {
                        "$artist - $title"
                    } else {
                        title ?: artist ?: ""
                    }
                    val map = mapOf("StreamTitle" to streamTitle)
                    _playbackState.update { it.copy(streamLiveInfo = StreamLiveInfo(map)) }
                }
            }
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            readStateFromExtras(extras)
        }

        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (command.customAction == PlayerService.PLAYER_SERVICE_METERED_CONNECTION) {
                args.classLoader = PlayerType::class.java.classLoader
                val playerType = BundleCompat.getParcelable(
                    args,
                    PlayerService.PLAYER_SERVICE_METERED_CONNECTION_PLAYER_TYPE,
                    PlayerType::class.java
                ) ?: PlayerType.RADIODROID
                _meteredConnectionEvents.tryEmit(playerType)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onDisconnected(controller: MediaController) {
            if (BuildConfig.DEBUG) Log.d(TAG, "MediaController disconnected")
            controller.removeListener(playerListener)
            mediaController = null
            controllerFuture = null
            _playbackState.update { it.copy(isConnected = false) }
        }
    }

    init {
        connect()
    }

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlayerService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                controller.addListener(playerListener)
                _playbackState.update { it.copy(isConnected = true) }
                readStateFromExtras(controller.sessionExtras)
                if (BuildConfig.DEBUG) Log.d(TAG, "MediaController successfully connected")
            } catch (e: Exception) {
                Log.e(TAG, "MediaController connection failed", e)
                controllerFuture = null
                _playbackState.update { it.copy(isConnected = false) }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(action: (MediaController) -> Unit) {
        val currentController = mediaController
        if (currentController != null && currentController.isConnected) {
            action(currentController)
            return
        }
        val future = controllerFuture ?: run {
            connect()
            controllerFuture
        } ?: return

        future.addListener({
            try {
                val controller = future.get()
                action(controller)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run action on MediaController", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun readStateFromExtras(extras: Bundle?) {
        if (extras == null) return
        extras.classLoader = MediaControllerRepository::class.java.classLoader

        val isPlaying = extras.getBoolean(PlayerService.STATE_IS_PLAYING, _playbackState.value.isPlaying)
        val playerState = BundleCompat.getParcelable(extras, PlayerService.STATE_PLAYER_STATE, PlayState::class.java)
            ?: _playbackState.value.playState
        val timerSeconds = extras.getLong(PlayerService.STATE_TIMER_SECONDS, _playbackState.value.sleepTimerSeconds)
        val liveInfo = BundleCompat.getParcelable(extras, PlayerService.STATE_METADATA_LIVE, StreamLiveInfo::class.java)
            ?: _playbackState.value.streamLiveInfo
        val station = BundleCompat.getParcelable(extras, PlayerService.STATE_CURRENT_STATION, DataRadioStation::class.java)
            ?: _playbackState.value.station
        val shoutcast = BundleCompat.getParcelable(extras, PlayerService.STATE_SHOUTCAST_INFO, ShoutcastInfo::class.java)
            ?: _playbackState.value.shoutcastInfo
        val isHls = extras.getBoolean(PlayerService.STATE_IS_HLS, _playbackState.value.isHls)
        val isRecording = extras.getBoolean(PlayerService.STATE_IS_RECORDING, _playbackState.value.isRecording)
        val recordFileName = extras.getString(PlayerService.STATE_RECORD_FILE_NAME)
        val transferredBytes = extras.getLong(PlayerService.STATE_TRANSFERRED_BYTES, _playbackState.value.transferredBytes)
        val bufferedSeconds = extras.getLong(PlayerService.STATE_BUFFERED_SECONDS, _playbackState.value.bufferedSeconds)
        val lastPlayStartTime = extras.getLong(PlayerService.STATE_LAST_PLAY_START_TIME, _playbackState.value.lastPlayStartTime)
        val pauseReason = BundleCompat.getParcelable(extras, PlayerService.STATE_PAUSE_REASON, PauseReason::class.java)
            ?: _playbackState.value.pauseReason
        val notificationActive = extras.getBoolean(PlayerService.STATE_NOTIFICATION_ACTIVE, _playbackState.value.isNotificationActive)

        _playbackState.update {
            it.copy(
                isPlaying = isPlaying,
                playState = playerState,
                station = station,
                streamLiveInfo = liveInfo,
                shoutcastInfo = shoutcast,
                isHls = isHls,
                isRecording = isRecording,
                recordFileName = recordFileName,
                transferredBytes = transferredBytes,
                bufferedSeconds = bufferedSeconds,
                sleepTimerSeconds = timerSeconds,
                pauseReason = pauseReason,
                isNotificationActive = notificationActive,
                lastPlayStartTime = lastPlayStartTime
            )
        }
    }

    private fun sendCustomCommand(action: String, args: Bundle = Bundle.EMPTY) {
        withController { controller ->
            controller.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
        }
    }

    fun playStation(station: DataRadioStation, isAlarm: Boolean = false) {
        withController { controller ->
            val mediaItem = station.toMediaItem(isAlarm = isAlarm, playImmediately = true)
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }

    fun setStation(station: DataRadioStation) {
        withController { controller ->
            val mediaItem = station.toMediaItem(playImmediately = false)
            controller.setMediaItem(mediaItem)
        }
    }

    fun pause(reason: PauseReason = PauseReason.USER) {
        val args = Bundle().apply { putParcelable(PlayerService.CMD_ARG_PAUSE_REASON, reason) }
        sendCustomCommand(PlayerService.CUSTOM_COMMAND_PAUSE, args)
    }

    fun resume() {
        withController { it.play() }
    }

    fun stop() {
        withController { it.stop() }
    }

    fun skipToNext() {
        withController { it.seekToNextMediaItem() }
    }

    fun skipToPrevious() {
        withController { it.seekToPreviousMediaItem() }
    }

    fun startRecording() {
        sendCustomCommand(PlayerService.CUSTOM_COMMAND_START_RECORDING)
    }

    fun stopRecording() {
        sendCustomCommand(PlayerService.CUSTOM_COMMAND_STOP_RECORDING)
    }

    fun addTimer(secondsAdd: Int) {
        val args = Bundle().apply { putInt(PlayerService.CMD_ARG_TIMER_SECONDS, secondsAdd) }
        sendCustomCommand(PlayerService.CUSTOM_COMMAND_ADD_TIMER, args)
    }

    fun clearTimer() {
        sendCustomCommand(PlayerService.CUSTOM_COMMAND_CLEAR_TIMER)
    }

    fun warnAboutMeteredConnection(playerType: PlayerType) {
        val args = Bundle().apply { putParcelable(PlayerService.CMD_ARG_PLAYER_TYPE, playerType) }
        sendCustomCommand(PlayerService.CUSTOM_COMMAND_WARN_METERED, args)
    }

    fun playAlarm(
        station: DataRadioStation,
        timerSeconds: Int,
        onStarted: (() -> Unit)? = null
    ) {
        withController { controller ->
            val mediaItem = station.toMediaItem(isAlarm = true, playImmediately = true)
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
            if (timerSeconds > 0) {
                addTimer(timerSeconds)
            }
            onStarted?.invoke()
        }
    }

    fun shutdownService() {
        try {
            if (BuildConfig.DEBUG) Log.d(TAG, "shutdownService")
            controllerFuture?.let { MediaController.releaseFuture(it) }
            controllerFuture = null
            mediaController = null
            _playbackState.update { PlaybackState(isConnected = false) }
            context.stopService(Intent(context, PlayerService::class.java))
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "shutdownService error: ${e.message}")
        }
    }

    fun isConnected(): Boolean = mediaController?.isConnected == true
    fun isPlaying(): Boolean = _playbackState.value.isPlaying
    fun getCurrentStation(): DataRadioStation? = _playbackState.value.station

    // RadioPlayerController implementation
    override fun play() { resume() }
    override fun pause() { pause(PauseReason.USER) }
    override fun skip() { skipToNext() }

    companion object {
        private const val TAG = "MediaControllerRepo"
    }
}
