package com.github.fschroffner.radiodroid3.players.exoplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.Id3Frame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.preference.PreferenceManager
import com.github.fschroffner.radiodroid3.BuildConfig
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.players.PlayState
import com.github.fschroffner.radiodroid3.players.PlayerWrapper
import com.github.fschroffner.radiodroid3.recording.RecordableListener
import com.github.fschroffner.radiodroid3.station.live.ShoutcastInfo
import com.github.fschroffner.radiodroid3.station.live.StreamLiveInfo
import okhttp3.OkHttpClient
import java.io.IOException

class ExoPlayerWrapper : PlayerWrapper, IcyDataSource.IcyDataSourceListener, Player.Listener {

    private val TAG = "ExoPlayerWrapper"

    private var player: ExoPlayer? = null
    private lateinit var stateListener: PlayerWrapper.PlayListener

    private var streamUrl: String? = null
    private var bandwidthMeter: DefaultBandwidthMeter? = null
    private var recordableListener: RecordableListener? = null

    private var totalTransferredBytes = 0L
    private var currentPlaybackTransferredBytes = 0L

    private var isHls = false
    private var isPlayingFlag = false
    private var isReceiverRegistered = false

    private var playerThreadHandler: Handler? = null
    private var context: Context? = null
    private var audioSource: MediaSource? = null
    private var fullStopTask: Runnable? = null

    private val networkChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val p = player
            val src = audioSource
            if (fullStopTask != null && p != null && src != null && Utils.hasAnyConnection(ctx)) {
                Log.i(TAG, "Regained connection. Resuming playback.")
                cancelStopTask()
                p.setMediaSource(src)
                p.prepare()
                p.playWhenReady = true
            }
        }
    }

    override fun playRemote(httpClient: OkHttpClient, streamUrl: String, context: Context, isAlarm: Boolean) {
        if (streamUrl != this.streamUrl) currentPlaybackTransferredBytes = 0
        this.context = context
        this.streamUrl = streamUrl

        cancelStopTask()
        stateListener.onStateChanged(PlayState.PrePlaying)

        player?.stop()

        if (player == null) {
            player = ExoPlayer.Builder(context).build().also { p ->
                p.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(if (isAlarm) C.USAGE_ALARM else C.USAGE_MEDIA)
                        .build(), false
                )
                p.addListener(this)
                p.addAnalyticsListener(AnalyticEventListener())
            }
        }

        if (playerThreadHandler == null) playerThreadHandler = Handler(Looper.getMainLooper())

        isHls = Utils.urlIndicatesHlsStream(streamUrl)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val retryTimeout = prefs.getInt("settings_retry_timeout", 10)
        val retryDelay = prefs.getInt("settings_retry_delay", 100)

        if (bandwidthMeter == null) bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()

        val dataSourceFactory = RadioDataSourceFactory(httpClient, bandwidthMeter!!, this, retryTimeout.toLong(), retryDelay.toLong())
        val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
        val policy = CustomLoadErrorHandlingPolicy()

        audioSource = if (!isHls) {
            ProgressiveMediaSource.Factory(dataSourceFactory).setLoadErrorHandlingPolicy(policy).createMediaSource(mediaItem)
        } else {
            HlsMediaSource.Factory(dataSourceFactory).setLoadErrorHandlingPolicy(policy).createMediaSource(mediaItem)
        }

        player!!.setMediaSource(audioSource!!)
        player!!.prepare()
        player!!.playWhenReady = true

        if (!isReceiverRegistered) {
            val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(networkChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(networkChangedReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    override fun pause() {
        Log.i(TAG, "Pause. Stopping exoplayer.")
        cancelStopTask()
        player?.let {
            unregisterNetworkReceiver()
            it.stop()
            it.release()
            player = null
        }
    }

    override fun stop() {
        Log.i(TAG, "Stopping exoplayer.")
        cancelStopTask()
        player?.let {
            unregisterNetworkReceiver()
            it.stop()
            it.release()
            player = null
        }
        stopRecording()
    }

    /**
     * Exposes the internal ExoPlayer as a Media3 [Player] so it can be attached to a
     * androidx.media3.session.MediaSession. Returns null while no playback is active
     * (the player is created lazily on [playRemote] and released on [pause]/[stop]).
     */
    fun getMedia3Player(): Player? = player

    override fun isPlaying() = player != null && isPlayingFlag

    override fun getBufferedMs(): Long {
        val p = player ?: return 0
        return p.bufferedPosition - p.currentPosition
    }

    override fun getAudioSessionId() = player?.audioSessionId ?: 0

    override fun getTotalTransferredBytes() = totalTransferredBytes

    override fun getCurrentPlaybackTransferredBytes() = currentPlaybackTransferredBytes

    override fun isLocal() = true

    override fun setVolume(newVolume: Float) { player?.volume = newVolume }

    override fun setStateListener(listener: PlayerWrapper.PlayListener) { stateListener = listener }

    override fun onDataSourceConnected() {}

    override fun onDataSourceConnectionLost() {}

    override fun onMetadata(metadata: Metadata) {
        if (BuildConfig.DEBUG) Log.d(TAG, "META: $metadata")
        for (i in 0 until metadata.length()) {
            val entry = metadata.get(i) ?: continue
            when (entry) {
                is IcyInfo -> {
                    Log.d(TAG, "IcyInfo: $entry")
                    entry.title?.let {
                        onDataSourceStreamLiveInfo(StreamLiveInfo(mapOf("StreamTitle" to it)))
                    }
                }
                is IcyHeaders -> {
                    Log.d(TAG, "IcyHeaders: $entry")
                    onDataSourceShoutcastInfo(ShoutcastInfo(entry))
                }
                is Id3Frame -> {
                    Log.d(TAG, "id3 metadata: $entry")
                    if (entry is TextInformationFrame) {
                        val frameId = entry.id
                        if ((frameId == "TIT2" || frameId == "TT2") && entry.values.isNotEmpty()) {
                            onDataSourceStreamLiveInfo(StreamLiveInfo(mapOf("StreamTitle" to entry.values[0])))
                        } else if ((frameId == "TPE1" || frameId == "TP1") && entry.values.isNotEmpty()) {
                            onDataSourceStreamLiveInfo(StreamLiveInfo(mapOf("StreamTitle" to entry.values[0])))
                        }
                    }
                }
            }
        }
    }

    override fun onDataSourceConnectionLostIrrecoverably() {
        Log.i(TAG, "Connection lost irrecoverably.")
    }

    fun resumeWhenNetworkConnected() {
        playerThreadHandler!!.post {
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(context!!)
            val resumeWithin = sharedPref.getInt("settings_resume_within", 60)
            if (resumeWithin > 0) {
                Log.d(TAG, "Trying to resume playback within ${resumeWithin}s.")
                cancelStopTask()
                fullStopTask = Runnable {
                    stop()
                    stateListener.onPlayerError(R.string.giving_up_resume)
                    fullStopTask = null
                }
                playerThreadHandler!!.postDelayed(fullStopTask!!, resumeWithin * 1000L)
                stateListener.onPlayerWarning(R.string.warning_no_network_trying_resume)
            } else {
                stop()
                stateListener.onPlayerError(R.string.error_stream_reconnect_timeout)
            }
        }
    }

    override fun onDataSourceShoutcastInfo(shoutcastInfo: ShoutcastInfo?) {
        stateListener.onDataSourceShoutcastInfo(shoutcastInfo, false)
    }

    override fun onDataSourceStreamLiveInfo(streamLiveInfo: StreamLiveInfo) {
        stateListener.onDataSourceStreamLiveInfo(streamLiveInfo)
    }

    override fun onDataSourceBytesRead(buffer: ByteArray, offset: Int, length: Int) {
        totalTransferredBytes += length
        currentPlaybackTransferredBytes += length
        recordableListener?.onBytesAvailable(buffer, offset, length)
    }

    override fun canRecord() = player != null

    override fun startRecording(recordableListener: RecordableListener) {
        this.recordableListener = recordableListener
    }

    override fun stopRecording() {
        recordableListener?.onRecordingEnded()
        recordableListener = null
    }

    override fun isRecording() = recordableListener != null

    override fun getRecordNameFormattingArgs(): Map<String, String> = emptyMap()

    override fun getExtension() = if (isHls) "ts" else "mp3"

    private fun unregisterNetworkReceiver() {
        if (isReceiverRegistered) {
            context!!.unregisterReceiver(networkChangedReceiver)
            isReceiverRegistered = false
        }
    }

    private fun cancelStopTask() {
        fullStopTask?.let {
            playerThreadHandler?.removeCallbacks(it)
            fullStopTask = null
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {}

    override fun onPlayerErrorChanged(error: PlaybackException?) {
        Log.d(TAG, "Player error: ", error)
        if (fullStopTask != null) {
            stop()
            stateListener.onPlayerError(R.string.error_play_stream)
        }
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {}

    inner class CustomLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {
        private val MIN_RETRY_DELAY_MS = 10
        private val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context!!)

        private fun getSanitizedRetryDelaySettingsMs() =
            maxOf(sharedPrefs.getInt("settings_retry_delay", 100), MIN_RETRY_DELAY_MS)

        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long {
            var retryDelay = getSanitizedRetryDelaySettingsMs()
            val exception = loadErrorInfo.exception

            if (exception is HttpDataSource.InvalidContentTypeException) {
                stateListener.onPlayerError(R.string.error_play_stream)
                return C.TIME_UNSET
            }

            if (!Utils.hasAnyConnection(context!!)) {
                val resumeWithinS = sharedPrefs.getInt("settings_resume_within", 60)
                if (resumeWithinS > 0) {
                    resumeWhenNetworkConnected()
                    retryDelay = 1000 * resumeWithinS + retryDelay
                }
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Providing retry delay of ${retryDelay}ms " +
                        "error count: ${loadErrorInfo.errorCount}, " +
                        "exception ${exception.javaClass}, " +
                        "message: ${exception.message}")
            }
            return retryDelay.toLong()
        }

        override fun getMinimumLoadableRetryCount(dataType: Int) =
            sharedPrefs.getInt("settings_retry_timeout", 10) * 1000 / getSanitizedRetryDelaySettingsMs() + 1
    }

    private inner class AnalyticEventListener : AnalyticsListener {
        override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, playbackState: Int) {
            isPlayingFlag = playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING
            when (playbackState) {
                Player.STATE_READY -> {
                    cancelStopTask()
                    stateListener.onStateChanged(PlayState.Playing)
                }
                Player.STATE_BUFFERING -> stateListener.onStateChanged(PlayState.PrePlaying)
            }
        }
    }
}
