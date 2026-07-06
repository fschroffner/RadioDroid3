package net.programmierecke.radiodroid2.service

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.SimpleBasePlayer.State
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * A persistent Media3 [Player] facade that represents "the radio" to a
 * androidx.media3.session.MediaSession and, through it, to the
 * androidx.media3.session.DefaultMediaNotificationProvider.
 *
 * RadioDroid's real playback engine (an ExoPlayer wrapped in RadioPlayer/ExoPlayerWrapper) is
 * created lazily on play and fully released on pause/stop, and "next"/"previous" switch stations
 * rather than seeking a timeline. A MediaSessionService, however, requires the session to always
 * hold a valid Player. This facade bridges that gap: it stays alive for the whole service
 * lifetime, exposes the current playback state and station metadata to the notification, and
 * routes play/pause/stop commands back into [PlayerService]. Station switching (previous/next) is
 * handled through custom notification buttons in PlayerService rather than seek commands, so this
 * facade only advertises the commands it can actually serve.
 *
 * All interaction must happen on the [Looper] passed to the constructor (the main thread).
 */
class RadioMediaPlayer(
    looper: Looper,
    private val callback: Callback
) : SimpleBasePlayer(looper) {

    interface Callback {
        fun onPlay()
        fun onPause()
        fun onStop()
    }

    private var playbackStateInt: Int = Player.STATE_IDLE
    private var playWhenReadyFlag: Boolean = false
    private var currentMediaItem: MediaItem? = null
    private var currentMediaMetadata: MediaMetadata = MediaMetadata.EMPTY

    private val availableCommands: Player.Commands = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            // Lets a browsing controller (e.g. Android Auto) "set" a station's media item to play it.
            // The item is resolved and playback is started by PlayerService's session callback
            // (onAddMediaItems); handleSetMediaItems below is therefore a no-op.
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_RELEASE
        )
        .build()

    /**
     * Pushes a new radio state into the facade. Must be called on the player's application thread.
     * Passing [Player.STATE_IDLE] (or a null media item) yields an empty playlist, which lets the
     * MediaSessionService tear down the notification and leave the foreground state.
     */
    fun update(
        playbackState: Int,
        playWhenReady: Boolean,
        mediaItem: MediaItem?,
        mediaMetadata: MediaMetadata
    ) {
        this.playbackStateInt = playbackState
        this.playWhenReadyFlag = playWhenReady
        this.currentMediaItem = mediaItem
        this.currentMediaMetadata = mediaMetadata
        invalidateState()
    }

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(availableCommands)
            .setPlaybackState(playbackStateInt)
            .setPlayWhenReady(playWhenReadyFlag, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        val item = currentMediaItem
        // An empty playlist is only valid in STATE_IDLE/STATE_ENDED, so only publish a media item
        // while there is something to show.
        if (item != null && playbackStateInt != Player.STATE_IDLE && playbackStateInt != Player.STATE_ENDED) {
            val uid = item.mediaId.ifEmpty { "radiodroid_station" }
            val mediaItemData = MediaItemData.Builder(uid)
                .setMediaItem(item)
                .setMediaMetadata(currentMediaMetadata)
                .setIsSeekable(false)
                .setIsDynamic(true)
                .setDurationUs(C.TIME_UNSET)
                .build()
            builder.setPlaylist(listOf(mediaItemData))
            builder.setCurrentMediaItemIndex(0)
        }
        return builder.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) callback.onPlay() else callback.onPause()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        callback.onPlay()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        callback.onStop()
        return Futures.immediateVoidFuture()
    }

    /**
     * Browse-initiated playback is resolved and started by PlayerService's session callback
     * (onAddMediaItems), which returns an empty list. The radio engine drives the timeline exposed
     * through [getState], so there is nothing to apply here.
     */
    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        return Futures.immediateVoidFuture()
    }
}
