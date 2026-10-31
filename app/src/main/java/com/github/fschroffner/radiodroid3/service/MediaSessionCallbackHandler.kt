package com.github.fschroffner.radiodroid3.service

import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.github.fschroffner.radiodroid3.FavouriteManager
import com.github.fschroffner.radiodroid3.HistoryManager
import com.github.fschroffner.radiodroid3.player.MediaItemMapper
import com.github.fschroffner.radiodroid3.player.toDataRadioStation
import com.github.fschroffner.radiodroid3.players.selector.PlayerType
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Handles incoming MediaLibrarySession and MediaSession callbacks, routing commands
 * to the service's player logic.
 */
class MediaSessionCallbackHandler(
    private val browseTree: RadioBrowseTree,
    private val favouriteManager: FavouriteManager,
    private val historyManager: HistoryManager,
    private val delegate: PlayerActionDelegate
) : MediaLibrarySession.Callback {

    interface PlayerActionDelegate {
        fun onSkipToPrevious()
        fun onSkipToNext()
        fun onStopPlayback()
        fun onSetStation(station: DataRadioStation)
        fun onPlayCurrentStation()
        fun onPausePlayback(reason: PauseReason)
        fun onResumePlayback()
        fun onAddTimer(seconds: Int)
        fun onClearTimer()
        fun onWarnMeteredConnection(playerType: PlayerType)
        fun getServiceClassLoader(): ClassLoader
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val sessionCommands =
            MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(PlayerService.CUSTOM_COMMAND_PREVIOUS, Bundle.EMPTY))
                .add(SessionCommand(PlayerService.CUSTOM_COMMAND_NEXT, Bundle.EMPTY))
                .add(SessionCommand(PlayerService.CUSTOM_COMMAND_STOP, Bundle.EMPTY))
                .add(SessionCommand(PlayerService.CUSTOM_COMMAND_SET_STATION, Bundle.EMPTY))
                .add(SessionCommand(PlayerService.CUSTOM_COMMAND_PLAY_STATION, Bundle.EMPTY))
                .add(SessionCommand(PlayerService.CUSTOM_COMMAND_PAUSE, Bundle.EMPTY))
                .add(SessionCommand(PlayerService.CUSTOM_COMMAND_RESUME, Bundle.EMPTY))
                .add(SessionCommand(PlayerService.CUSTOM_COMMAND_ADD_TIMER, Bundle.EMPTY))
                .add(SessionCommand(PlayerService.CUSTOM_COMMAND_CLEAR_TIMER, Bundle.EMPTY))
                .add(SessionCommand(PlayerService.CUSTOM_COMMAND_WARN_METERED, Bundle.EMPTY))
                .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .build()
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        val item = mediaItems.firstOrNull() ?: return Futures.immediateFuture(mutableListOf())
        val station = item.toDataRadioStation()
            ?: browseTree.stationForMediaId(item.mediaId)
            ?: favouriteManager.getById(item.mediaId)
            ?: historyManager.getById(item.mediaId)
        val playImmediately = item.mediaMetadata.extras?.getBoolean(MediaItemMapper.EXTRA_PLAY_IMMEDIATELY, true) ?: true
        if (station != null) {
            delegate.onSetStation(station)
            if (playImmediately) {
                delegate.onPlayCurrentStation()
            }
        }
        return Futures.immediateFuture(mediaItems)
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val rootParams = LibraryParams.Builder().setExtras(browseTree.rootExtras()).build()
        return Futures.immediateFuture(LibraryResult.ofItem(browseTree.rootItem(), rootParams))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        Futures.immediateFuture(LibraryResult.ofItemList(browseTree.childrenOf(parentId), params))

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val item = browseTree.itemForMediaId(mediaId)
        return if (item != null) Futures.immediateFuture(LibraryResult.ofItem(item, null))
        else Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        args.classLoader = delegate.getServiceClassLoader()
        when (customCommand.customAction) {
            PlayerService.CUSTOM_COMMAND_PREVIOUS -> delegate.onSkipToPrevious()
            PlayerService.CUSTOM_COMMAND_NEXT -> delegate.onSkipToNext()
            PlayerService.CUSTOM_COMMAND_STOP -> delegate.onStopPlayback()
            PlayerService.CUSTOM_COMMAND_SET_STATION ->
                BundleCompat.getParcelable(args, PlayerService.CMD_ARG_STATION, DataRadioStation::class.java)
                    ?.let { delegate.onSetStation(it) }
            PlayerService.CUSTOM_COMMAND_PLAY_STATION -> {
                val station = BundleCompat.getParcelable(args, PlayerService.CMD_ARG_STATION, DataRadioStation::class.java)
                if (station != null) delegate.onSetStation(station)
                delegate.onPlayCurrentStation()
            }
            PlayerService.CUSTOM_COMMAND_PAUSE -> {
                val reason = BundleCompat.getParcelable(args, PlayerService.CMD_ARG_PAUSE_REASON, PauseReason::class.java)
                    ?: PauseReason.USER
                delegate.onPausePlayback(reason)
            }
            PlayerService.CUSTOM_COMMAND_RESUME -> delegate.onResumePlayback()
            PlayerService.CUSTOM_COMMAND_ADD_TIMER -> delegate.onAddTimer(args.getInt(PlayerService.CMD_ARG_TIMER_SECONDS, 0))
            PlayerService.CUSTOM_COMMAND_CLEAR_TIMER -> delegate.onClearTimer()
            PlayerService.CUSTOM_COMMAND_WARN_METERED ->
                BundleCompat.getParcelable(args, PlayerService.CMD_ARG_PLAYER_TYPE, PlayerType::class.java)
                    ?.let { delegate.onWarnMeteredConnection(it) }
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }
}
