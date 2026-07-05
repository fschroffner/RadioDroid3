package net.programmierecke.radiodroid2.players

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.media3.common.Player
import androidx.preference.PreferenceManager
import net.programmierecke.radiodroid2.BuildConfig
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.RadioDroidApp
import net.programmierecke.radiodroid2.Utils
import net.programmierecke.radiodroid2.players.exoplayer.ExoPlayerWrapper
import net.programmierecke.radiodroid2.players.mediaplayer.MediaPlayerWrapper
import net.programmierecke.radiodroid2.recording.Recordable
import net.programmierecke.radiodroid2.recording.RecordableListener
import net.programmierecke.radiodroid2.station.DataRadioStation
import net.programmierecke.radiodroid2.station.live.ShoutcastInfo
import net.programmierecke.radiodroid2.station.live.StreamLiveInfo
import java.util.concurrent.TimeUnit

class RadioPlayer(private val mainContext: Context) : PlayerWrapper.PlayListener, Recordable {

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
    private var playStationTask: PlayStationTask? = null
    private var streamName: String = ""

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

    fun play(stationURL: String, streamName: String, isAlarm: Boolean) {
        setState(PlayState.PrePlaying, -1)
        this.streamName = streamName

        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainContext.applicationContext)
        val connectTimeout = prefs.getInt("stream_connect_timeout", 4)
        val readTimeout = prefs.getInt("stream_read_timeout", 10)

        val radioDroidApp = mainContext.applicationContext as RadioDroidApp
        val client = radioDroidApp.newHttpClient()
            .connectTimeout(connectTimeout.toLong(), TimeUnit.SECONDS)
            .readTimeout(readTimeout.toLong(), TimeUnit.SECONDS)
            .build()

        playerThreadHandler.post { currentPlayer.playRemote(client, stationURL, mainContext, isAlarm) }
    }

    fun play(station: DataRadioStation, isAlarm: Boolean) {
        setState(PlayState.PrePlaying, -1)
        playStationTask = PlayStationTask(station, mainContext,
            { RadioPlayer@ this.play(station.playableUrl!!, station.Name, isAlarm) },
            { result ->
                playStationTask = null
                if (result == PlayStationTask.ExecutionResult.FAILURE) {
                    onPlayerError(R.string.error_station_load)
                }
            })
        playStationTask!!.execute()
    }

    private fun cancelStationLinkRetrieval() {
        playStationTask?.cancel(true)
        playStationTask = null
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

    override fun canRecord() = currentPlayer.canRecord()

    override fun startRecording(recordableListener: RecordableListener) = currentPlayer.startRecording(recordableListener)

    override fun stopRecording() = currentPlayer.stopRecording()

    override fun isRecording() = currentPlayer.isRecording()

    override fun getRecordNameFormattingArgs(): Map<String, String> {
        return buildMap {
            put("station", Utils.sanitizeName(streamName))
            val info = lastLiveInfo
            if (info != null) {
                put("artist", Utils.sanitizeName(info.artist))
                put("track", Utils.sanitizeName(info.track))
            } else {
                put("artist", "-")
                put("track", "-")
            }
        }
    }

    override fun getExtension() = currentPlayer.getExtension()

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
        shoutcastInfo?.let { playerListener.foundShoutcastStream(it, isHls) }
    }

    override fun onDataSourceStreamLiveInfo(liveInfo: StreamLiveInfo) {
        lastLiveInfo = liveInfo
        playerListener.foundLiveStreamInfo(liveInfo)
    }
}
