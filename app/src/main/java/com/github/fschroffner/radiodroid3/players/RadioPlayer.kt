package com.github.fschroffner.radiodroid3.players

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.media3.common.Player
import androidx.preference.PreferenceManager
import com.github.fschroffner.radiodroid3.BuildConfig
import com.github.fschroffner.radiodroid3.FavouriteManager
import com.github.fschroffner.radiodroid3.HistoryManager
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.data.loading.LoadingStateHolder
import com.github.fschroffner.radiodroid3.di.AppEntryPoint
import com.github.fschroffner.radiodroid3.players.exoplayer.ExoPlayerWrapper
import com.github.fschroffner.radiodroid3.players.mediaplayer.MediaPlayerWrapper
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.live.ShoutcastInfo
import com.github.fschroffner.radiodroid3.station.live.StreamLiveInfo
import dagger.hilt.EntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class RadioPlayer(
    private val mainContext: Context,
    private val injectedHttpClient: OkHttpClient? = null,
    private val injectedHistoryManager: HistoryManager? = null,
    private val injectedFavouriteManager: FavouriteManager? = null
) : PlayerWrapper.PlayListener {

    private val TAG = "RadioPlayer"

    interface PlayerListener {
        fun onStateChanged(status: PlayState, audioSessionId: Int)
        fun onPlayerWarning(messageId: Int)
        fun onPlayerError(messageId: Int)
        fun onBufferedTimeUpdate(bufferedMs: Long)
        fun foundShoutcastStream(bitrate: ShoutcastInfo, isHls: Boolean)
        fun foundLiveStreamInfo(liveInfo: StreamLiveInfo)
    }

    private val currentPlayer: PlayerWrapper
    private val playerThread: HandlerThread?
    private val playerThreadHandler: Handler
    private lateinit var playerListener: PlayerListener
    private var playState = PlayState.Idle
    private var lastLiveInfo: StreamLiveInfo? = null
    private var streamName: String = ""

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playJob: Job? = null

    private val bufferCheckRunnable = object : Runnable {
        override fun run() {
            val bufferTimeMs = currentPlayer.getBufferedMs()
            playerListener.onBufferedTimeUpdate(bufferTimeMs)
            if (BuildConfig.DEBUG) Log.d(TAG, "buffered $bufferTimeMs ms.")
            playerThreadHandler.postDelayed(this, 2000)
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            playerThreadHandler = Handler(Looper.getMainLooper())
            currentPlayer = ExoPlayerWrapper()
            playerThread = null
        } else {
            playerThread = HandlerThread("MediaPlayerThread").also { it.start() }
            playerThreadHandler = Handler(playerThread.looper)
            currentPlayer = MediaPlayerWrapper(playerThreadHandler)
        }
        currentPlayer.setStateListener(this)
    }

    fun play(stationURL: String, streamName: String) {
        setState(PlayState.PrePlaying, -1)
        this.streamName = streamName

        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainContext.applicationContext)
        val connectTimeout = try {
            prefs.getInt("stream_connect_timeout", 30)
        } catch (_: Exception) {
            prefs.getString("stream_connect_timeout", null)?.toIntOrNull() ?: 30
        }.coerceAtLeast(10)
        val readTimeout = try {
            prefs.getInt("stream_read_timeout", 30)
        } catch (_: Exception) {
            prefs.getString("stream_read_timeout", null)?.toIntOrNull() ?: 30
        }.coerceAtLeast(10)

        val baseClient = injectedHttpClient
            ?: EntryPoints.get(mainContext.applicationContext, AppEntryPoint::class.java).okHttpClient()
        val client = baseClient.newBuilder()
            .connectTimeout(connectTimeout.toLong(), TimeUnit.SECONDS)
            .readTimeout(readTimeout.toLong(), TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()

        playerThreadHandler.post { currentPlayer.playRemote(client, stationURL, mainContext) }
    }

    fun play(station: DataRadioStation) {
        setState(PlayState.PrePlaying, -1)
        cancelStationLinkRetrieval()

        val hm = injectedHistoryManager ?: EntryPoints.get(mainContext.applicationContext, AppEntryPoint::class.java).historyManager()
        hm.add(station)

        val fm = injectedFavouriteManager ?: EntryPoints.get(mainContext.applicationContext, AppEntryPoint::class.java).favouriteManager()
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(mainContext)
        if (sharedPref.getBoolean("auto_favorite", false)) {
            if (!fm.has(station.StationUuid)) {
                fm.add(station)
                Toast.makeText(mainContext, mainContext.getString(R.string.notify_autostarred), Toast.LENGTH_SHORT).show()
            }
        }

        LoadingStateHolder.show()

        playJob = coroutineScope.launch {
            val client = injectedHttpClient ?: EntryPoints.get(mainContext.applicationContext, AppEntryPoint::class.java).okHttpClient()
            val resolvedUrl = withContext(Dispatchers.IO) {
                var streamUrl: String? = null
                if (station.hasValidUuid()) {
                    try {
                        streamUrl = Utils.getRealStationLink(client, mainContext.applicationContext, station.StationUuid)
                    } catch (e: Exception) {
                        Log.w(TAG, "getRealStationLink failed: $e")
                    }
                }
                if (streamUrl.isNullOrBlank()) {
                    streamUrl = station.playableUrl?.takeIf { it.isNotBlank() }
                        ?: station.StreamUrl.takeIf { it.isNotBlank() }
                }
                val resolved = streamUrl?.let { Utils.resolvePlaylistIfNeeded(client, it) }
                resolved?.takeIf { it.isNotBlank() }
                    ?: station.playableUrl?.takeIf { it.isNotBlank() }
                    ?: station.StreamUrl.takeIf { it.isNotBlank() }
            }

            LoadingStateHolder.hide()

            if (resolvedUrl != null) {
                station.playableUrl = resolvedUrl
                this@RadioPlayer.play(resolvedUrl, station.Name)
            } else {
                onPlayerError(R.string.error_station_load)
            }
        }
    }

    private fun cancelStationLinkRetrieval() {
        if (playJob?.isActive == true) {
            playJob?.cancel()
            LoadingStateHolder.hide()
        }
        playJob = null
    }

    fun pause() {
        cancelStationLinkRetrieval()
        playerThreadHandler.post {
            if (playState == PlayState.Idle || playState == PlayState.Paused) return@post
            val audioSessionId = getAudioSessionId()
            currentPlayer.pause()
            if (BuildConfig.DEBUG) playerThreadHandler.removeCallbacks(bufferCheckRunnable)
            setState(PlayState.Paused, audioSessionId)
        }
    }

    fun stop() {
        if (playState == PlayState.Idle) return
        cancelStationLinkRetrieval()
        playerThreadHandler.post {
            val audioSessionId = getAudioSessionId()
            currentPlayer.stop()
            if (BuildConfig.DEBUG) playerThreadHandler.removeCallbacks(bufferCheckRunnable)
            setState(PlayState.Idle, audioSessionId)
        }
    }

    fun destroy() {
        cancelStationLinkRetrieval()
        coroutineScope.cancel()
        stop()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            playerThread?.looper?.let { playerThreadHandler.post { playerThread.quit() } }
        }
    }

    fun isPlaying() = playState == PlayState.PrePlaying || playState == PlayState.Playing

    fun getAudioSessionId() = currentPlayer.getAudioSessionId()

    /**
     * Returns the underlying Media3 [Player] when the active backend is the ExoPlayer and
     * playback resources are allocated, otherwise null. Used to back a Media3 MediaSession.
     */
    fun getMedia3Player(): Player? = (currentPlayer as? ExoPlayerWrapper)?.getMedia3Player()

    fun setVolume(volume: Float) = currentPlayer.setVolume(volume)

    fun runInPlayerThread(runnable: Runnable) = playerThreadHandler.post(runnable)

    fun setPlayerListener(listener: PlayerListener) { playerListener = listener }

    fun getPlayState() = playState

    private fun setState(state: PlayState, audioSessionId: Int) {
        if (BuildConfig.DEBUG) Log.d(TAG, "set state '${state.name}'")
        if (playState == state) return
        if (BuildConfig.DEBUG) {
            playerThreadHandler.removeCallbacks(bufferCheckRunnable)
            if (state == PlayState.Playing) playerThreadHandler.post(bufferCheckRunnable)
        }
        playState = state
        playerListener.onStateChanged(state, audioSessionId)
    }

    fun getTotalTransferredBytes() = currentPlayer.getTotalTransferredBytes()

    fun getCurrentPlaybackTransferredBytes() = currentPlayer.getCurrentPlaybackTransferredBytes()

    fun getBufferedSeconds() = currentPlayer.getBufferedMs() / 1000

    fun isLocal() = currentPlayer.isLocal()

    override fun onStateChanged(state: PlayState) = setState(state, getAudioSessionId())

    override fun onPlayerWarning(messageId: Int) {
        playerThreadHandler.post { playerListener.onPlayerWarning(messageId) }
    }

    override fun onPlayerError(messageId: Int) {
        pause()
        playerThreadHandler.post { playerListener.onPlayerError(messageId) }
    }

    override fun onDataSourceShoutcastInfo(shoutcastInfo: ShoutcastInfo?, isHls: Boolean) {
        playerThreadHandler.post {
            shoutcastInfo?.let { playerListener.foundShoutcastStream(it, isHls) }
        }
    }

    override fun onDataSourceStreamLiveInfo(liveInfo: StreamLiveInfo) {
        playerThreadHandler.post {
            lastLiveInfo = liveInfo
            playerListener.foundLiveStreamInfo(liveInfo)
        }
    }
}
