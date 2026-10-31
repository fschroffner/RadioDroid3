package com.github.fschroffner.radiodroid3.service

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.SessionCommand
import com.github.fschroffner.radiodroid3.data.datastore.AppPreferencesDataStore
import com.github.fschroffner.radiodroid3.players.selector.PlayerType

/**
 * Handles calculation and state of the metered connection pause cooldown.
 */
class MeteredConnectionCooldownTracker(
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    var lastMeteredConnectionWarningTime: Long = 0L
        private set

    fun recordMeteredPause() {
        lastMeteredConnectionWarningTime = timeProvider()
    }

    fun resetCooldown() {
        lastMeteredConnectionWarningTime = 0L
    }

    fun isCooldownActive(): Boolean {
        if (lastMeteredConnectionWarningTime == 0L) return false
        val now = timeProvider()
        val delta = now - lastMeteredConnectionWarningTime
        return delta in 0 until MeteredConnectionManager.METERED_CONNECTION_WARNING_COOLDOWN
    }
}

/**
 * Handles detection of metered connections (cellular data),
 * plays warning audio effects, and coordinates warning cooldowns.
 */
class MeteredConnectionManager(
    private val context: Context,
    private val handler: Handler,
    private val appPreferencesDataStore: AppPreferencesDataStore,
    private val listener: Listener,
    private val cooldownTracker: MeteredConnectionCooldownTracker = MeteredConnectionCooldownTracker()
) {
    interface Listener {
        fun onMeteredWarningTriggered(playerType: PlayerType)
        fun onPauseForMetered()
    }

    companion object {
        const val METERED_CONNECTION_WARNING_COOLDOWN = 20 * 1000L
    }

    private val audioWarning = AudioWarning(handler, object : AudioWarning.Callback {
        override fun onWarningStarted() {}
        override fun onWarningFinished() {}
    })

    private val connectivityChecker = ConnectivityChecker()

    private val connectivityCallback = object : ConnectivityChecker.ConnectivityCallback {
        override fun onConnectivityChanged(connected: Boolean, connectionType: ConnectivityChecker.ConnectionType) {
            if (connectionType == ConnectivityChecker.ConnectionType.METERED &&
                appPreferencesDataStore.getBoolean(PlayerService.METERED_CONNECTION_WARNING_KEY, false)
            ) {
                listener.onMeteredWarningTriggered(PlayerType.RADIODROID)
            }
        }
    }

    fun startListening() {
        if (appPreferencesDataStore.getBoolean(PlayerService.METERED_CONNECTION_WARNING_KEY, false)) {
            connectivityChecker.startListening(context, connectivityCallback)
        }
    }

    fun stopListening() {
        connectivityChecker.stopListening(context)
    }

    fun forceStopAudioWarning() {
        audioWarning.forceStop()
    }

    fun recordMeteredPause() = cooldownTracker.recordMeteredPause()

    fun resetCooldown() = cooldownTracker.resetCooldown()

    fun isCooldownActive(): Boolean = cooldownTracker.isCooldownActive()

    fun warnAboutMeteredConnection(playerType: PlayerType, media3Session: MediaLibrarySession?) {
        stopListening()
        recordMeteredPause()
        listener.onPauseForMetered()

        audioWarning.play()

        val bundle = Bundle().apply {
            putParcelable(PlayerService.PLAYER_SERVICE_METERED_CONNECTION_PLAYER_TYPE, playerType as Parcelable)
        }
        if (media3Session != null) {
            media3Session.broadcastCustomCommand(
                SessionCommand(PlayerService.PLAYER_SERVICE_METERED_CONNECTION, Bundle.EMPTY),
                bundle
            )
        }
    }
}
