package net.programmierecke.radiodroid2.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import net.programmierecke.radiodroid2.BuildConfig
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.players.PlayState
import net.programmierecke.radiodroid2.players.selector.PlayerType
import net.programmierecke.radiodroid2.station.DataRadioStation
import net.programmierecke.radiodroid2.station.live.ShoutcastInfo
import net.programmierecke.radiodroid2.station.live.StreamLiveInfo

/**
 * In-app entry point to the playback service.
 *
 * The service was previously reached through an AIDL `IPlayerService` binder. As part of the Media3
 * migration this now connects through a Media3 [MediaController]:
 *
 * - Transport and radio-specific actions are sent as custom [SessionCommand]s handled by
 *   [PlayerService].
 * - The radio-specific state that Media3's standard player state cannot express (transferred bytes,
 *   buffered seconds, Shoutcast info, HLS flag, recording status, pause reason, sleep timer, ...) is
 *   mirrored into the session extras by the service and read back here synchronously, preserving the
 *   original getter API used across the app.
 *
 * All [MediaController] interaction happens on the application main thread.
 */
object PlayerServiceUtil {
    private const val TAG = "PlayerServiceUtil"

    private var mainContext: Context? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // Signatures of the last UI-significant state/metadata broadcast. The service refreshes the
    // session extras every second while playing (for byte/buffer counters), so onExtrasChanged fires
    // continuously; these guard against turning every such tick into an app-wide UI refresh.
    private var lastStateSignature: String? = null
    private var lastMetaSignature: String? = null

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            // The session extras carry all radio-specific state. Because setSessionExtras delivers
            // asynchronously, the service's own state/meta broadcasts can arrive before the fresh
            // extras do; re-broadcasting here (only when the significant state actually changed)
            // guarantees listeners get a chance to read an up-to-date snapshot.
            extras.classLoader = PlayerServiceUtil::class.java.classLoader
            val ctx = mainContext ?: return
            val lbm = LocalBroadcastManager.getInstance(ctx)

            val stateSignature = buildStateSignature(extras)
            if (stateSignature != lastStateSignature) {
                lastStateSignature = stateSignature
                lbm.sendBroadcast(Intent(PlayerService.PLAYER_SERVICE_STATE_CHANGE))
            }
            val metaSignature = buildMetaSignature(extras)
            if (metaSignature != lastMetaSignature) {
                lastMetaSignature = metaSignature
                lbm.sendBroadcast(Intent(PlayerService.PLAYER_SERVICE_META_UPDATE))
            }
        }

        override fun onDisconnected(controller: MediaController) {
            if (BuildConfig.DEBUG) Log.d(TAG, "MediaController disconnected")
            releaseController()
        }
    }

    private fun buildStateSignature(extras: Bundle): String {
        val playerState = BundleCompat.getParcelable(extras, PlayerService.STATE_PLAYER_STATE, PlayState::class.java)
        val pauseReason = BundleCompat.getParcelable(extras, PlayerService.STATE_PAUSE_REASON, PauseReason::class.java)
        return listOf(
            extras.getBoolean(PlayerService.STATE_IS_PLAYING, false),
            playerState?.name,
            extras.getString(PlayerService.STATE_STATION_ID),
            extras.getBoolean(PlayerService.STATE_IS_RECORDING, false),
            extras.getString(PlayerService.STATE_RECORD_FILE_NAME),
            pauseReason?.name,
            extras.getBoolean(PlayerService.STATE_NOTIFICATION_ACTIVE, false),
            extras.getBoolean(PlayerService.STATE_IS_HLS, false)
        ).joinToString("|")
    }

    private fun buildMetaSignature(extras: Bundle): String {
        val liveInfo = BundleCompat.getParcelable(extras, PlayerService.STATE_METADATA_LIVE, StreamLiveInfo::class.java)
        val shoutcast = BundleCompat.getParcelable(extras, PlayerService.STATE_SHOUTCAST_INFO, ShoutcastInfo::class.java)
        return listOf(
            extras.getString(PlayerService.STATE_STATION_ID),
            liveInfo?.title,
            shoutcast?.bitrate,
            shoutcast?.audioName
        ).joinToString("|")
    }

    @JvmStatic
    fun startService(context: Context) = connect(context)

    @JvmStatic
    fun bindService(context: Context) = connect(context)

    private fun connect(context: Context) {
        if (controllerFuture != null) return
        mainContext = context.applicationContext
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                mediaController = future.get()
                if (BuildConfig.DEBUG) Log.d("PLAYER", "MediaController connected")
                mainContext?.let {
                    LocalBroadcastManager.getInstance(it)
                        .sendBroadcast(Intent(PlayerService.PLAYER_SERVICE_BOUND))
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaController connection failed", e)
                controllerFuture = null
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun releaseController() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
        lastStateSignature = null
        lastMetaSignature = null
    }

    @JvmStatic
    fun shutdownService() {
        val ctx = mainContext ?: return
        try {
            if (BuildConfig.DEBUG) Log.d(TAG, "PlayerServiceUtil: shutdownService")
            releaseController()
            ctx.stopService(Intent(ctx, PlayerService::class.java))
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "PlayerServiceUtil: shutdownService E001:${e.message}")
        }
    }

    private fun sendCommand(action: String, args: Bundle = Bundle.EMPTY) {
        val controller = mediaController ?: return
        controller.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
    }

    /** Latest radio state mirrored by the service, with the app class loader set for Parcelables. */
    private fun stateExtras(): Bundle? {
        val extras = mediaController?.sessionExtras ?: return null
        extras.classLoader = PlayerServiceUtil::class.java.classLoader
        return extras
    }

    @JvmStatic
    fun isServiceBound(): Boolean = mediaController?.isConnected == true

    @JvmStatic
    fun isPlaying(): Boolean = stateExtras()?.getBoolean(PlayerService.STATE_IS_PLAYING, false) ?: false

    @JvmStatic
    fun getPlayerState(): PlayState =
        stateExtras()?.let { BundleCompat.getParcelable(it, PlayerService.STATE_PLAYER_STATE, PlayState::class.java) }
            ?: PlayState.Idle

    @JvmStatic
    fun stop() = sendCommand(PlayerService.CUSTOM_COMMAND_STOP)

    @JvmStatic
    fun play(station: DataRadioStation) {
        val args = Bundle().apply {
            putParcelable(PlayerService.CMD_ARG_STATION, station)
            putBoolean(PlayerService.CMD_ARG_IS_ALARM, false)
        }
        sendCommand(PlayerService.CUSTOM_COMMAND_PLAY_STATION, args)
    }

    @JvmStatic
    fun setStation(station: DataRadioStation) {
        val args = Bundle().apply { putParcelable(PlayerService.CMD_ARG_STATION, station) }
        sendCommand(PlayerService.CUSTOM_COMMAND_SET_STATION, args)
    }

    /**
     * Connects to the service if needed and, once the [MediaController] is ready, plays [station] as
     * an alarm and arms the sleep timer for [timerSeconds]. [onStarted] runs on the main thread once
     * the commands have been dispatched (or the connection failed). Used by
     * net.programmierecke.radiodroid2.alarm.AlarmReceiver, which previously reached the service
     * through the removed AIDL binder.
     */
    @JvmStatic
    @JvmOverloads
    fun playAlarm(
        context: Context,
        station: DataRadioStation,
        timerSeconds: Int,
        onStarted: Runnable? = null
    ) {
        val appCtx = context.applicationContext
        connect(appCtx)
        val future = controllerFuture
        if (future == null) {
            onStarted?.run()
            return
        }
        future.addListener({
            mediaController?.let { controller ->
                val args = Bundle().apply {
                    putParcelable(PlayerService.CMD_ARG_STATION, station)
                    putBoolean(PlayerService.CMD_ARG_IS_ALARM, true)
                }
                controller.sendCustomCommand(
                    SessionCommand(PlayerService.CUSTOM_COMMAND_PLAY_STATION, Bundle.EMPTY), args
                )
                if (timerSeconds > 0) {
                    val timerArgs = Bundle().apply {
                        putInt(PlayerService.CMD_ARG_TIMER_SECONDS, timerSeconds)
                    }
                    controller.sendCustomCommand(
                        SessionCommand(PlayerService.CUSTOM_COMMAND_ADD_TIMER, Bundle.EMPTY), timerArgs
                    )
                }
            }
            onStarted?.run()
        }, ContextCompat.getMainExecutor(appCtx))
    }

    @JvmStatic
    fun skipToNext() = sendCommand(PlayerService.CUSTOM_COMMAND_NEXT)

    @JvmStatic
    fun skipToPrevious() = sendCommand(PlayerService.CUSTOM_COMMAND_PREVIOUS)

    @JvmStatic
    fun pause(pauseReason: PauseReason) {
        val args = Bundle().apply { putParcelable(PlayerService.CMD_ARG_PAUSE_REASON, pauseReason) }
        sendCommand(PlayerService.CUSTOM_COMMAND_PAUSE, args)
    }

    @JvmStatic
    fun resume() = sendCommand(PlayerService.CUSTOM_COMMAND_RESUME)

    @JvmStatic
    fun clearTimer() = sendCommand(PlayerService.CUSTOM_COMMAND_CLEAR_TIMER)

    @JvmStatic
    fun addTimer(secondsAdd: Int) {
        val args = Bundle().apply { putInt(PlayerService.CMD_ARG_TIMER_SECONDS, secondsAdd) }
        sendCommand(PlayerService.CUSTOM_COMMAND_ADD_TIMER, args)
    }

    @JvmStatic
    fun getTimerSeconds(): Long = stateExtras()?.getLong(PlayerService.STATE_TIMER_SECONDS, 0L) ?: 0L

    @JvmStatic
    fun getMetadataLive(): StreamLiveInfo =
        stateExtras()?.let { BundleCompat.getParcelable(it, PlayerService.STATE_METADATA_LIVE, StreamLiveInfo::class.java) }
            ?: StreamLiveInfo(null)

    @JvmStatic
    fun getStationId(): String? = stateExtras()?.getString(PlayerService.STATE_STATION_ID)

    @JvmStatic
    fun getCurrentStation(): DataRadioStation? =
        stateExtras()?.let { BundleCompat.getParcelable(it, PlayerService.STATE_CURRENT_STATION, DataRadioStation::class.java) }

    @JvmStatic
    fun getStationIcon(holder: ImageView, fromUrl: String?) {
        if (fromUrl.isNullOrBlank()) return
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 70f, mainContext!!.resources.displayMetrics)
        val placeholder = AppCompatResources.getDrawable(holder.context, R.drawable.ic_photo_24dp)
        Picasso.get().load(fromUrl).placeholder(placeholder!!).resize(px.toInt(), 0)
            .networkPolicy(NetworkPolicy.OFFLINE)
            .into(holder, object : Callback {
                override fun onSuccess() {}
                override fun onError(e: Exception) {
                    Picasso.get().load(fromUrl).placeholder(placeholder).resize(px.toInt(), 0)
                        .networkPolicy(NetworkPolicy.NO_CACHE).into(holder)
                }
            })
    }

    @JvmStatic
    fun getShoutcastInfo(): ShoutcastInfo? =
        stateExtras()?.let { BundleCompat.getParcelable(it, PlayerService.STATE_SHOUTCAST_INFO, ShoutcastInfo::class.java) }

    @JvmStatic
    fun startRecording() = sendCommand(PlayerService.CUSTOM_COMMAND_START_RECORDING)

    @JvmStatic
    fun stopRecording() = sendCommand(PlayerService.CUSTOM_COMMAND_STOP_RECORDING)

    @JvmStatic
    fun isRecording(): Boolean = stateExtras()?.getBoolean(PlayerService.STATE_IS_RECORDING, false) ?: false

    @JvmStatic
    fun getCurrentRecordFileName(): String? = stateExtras()?.getString(PlayerService.STATE_RECORD_FILE_NAME)

    @JvmStatic
    fun getIsHls(): Boolean = stateExtras()?.getBoolean(PlayerService.STATE_IS_HLS, false) ?: false

    @JvmStatic
    fun getTransferredBytes(): Long = stateExtras()?.getLong(PlayerService.STATE_TRANSFERRED_BYTES, 0L) ?: 0L

    @JvmStatic
    fun getBufferedSeconds(): Long = stateExtras()?.getLong(PlayerService.STATE_BUFFERED_SECONDS, 0L) ?: 0L

    @JvmStatic
    fun getLastPlayStartTime(): Long = stateExtras()?.getLong(PlayerService.STATE_LAST_PLAY_START_TIME, 0L) ?: 0L

    @JvmStatic
    fun getPauseReason(): PauseReason =
        stateExtras()?.let { BundleCompat.getParcelable(it, PlayerService.STATE_PAUSE_REASON, PauseReason::class.java) }
            ?: PauseReason.NONE

    @JvmStatic
    fun warnAboutMeteredConnection(playerType: PlayerType) {
        val args = Bundle().apply { putParcelable(PlayerService.CMD_ARG_PLAYER_TYPE, playerType) }
        sendCommand(PlayerService.CUSTOM_COMMAND_WARN_METERED, args)
    }

    @JvmStatic
    fun isNotificationActive(): Boolean =
        stateExtras()?.getBoolean(PlayerService.STATE_NOTIFICATION_ACTIVE, false) ?: false
}
