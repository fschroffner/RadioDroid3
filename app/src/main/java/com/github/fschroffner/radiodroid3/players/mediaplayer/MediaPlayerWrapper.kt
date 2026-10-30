package com.github.fschroffner.radiodroid3.players.mediaplayer

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.util.Log
import com.github.fschroffner.radiodroid3.BuildConfig
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.players.PlayState
import com.github.fschroffner.radiodroid3.players.PlayerWrapper
import com.github.fschroffner.radiodroid3.station.live.ShoutcastInfo
import com.github.fschroffner.radiodroid3.station.live.StreamLiveInfo
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class MediaPlayerWrapper(private val playerThreadHandler: Handler) : PlayerWrapper, StreamProxyListener {

    private val TAG = "MediaPlayerWrapper"

    private var mediaPlayer: MediaPlayer? = null
    private var proxy: StreamProxy? = null
    private lateinit var stateListener: PlayerWrapper.PlayListener

    private var streamUrl: String? = null
    private var context: Context? = null
    private var isHls = false

    private var totalTransferredBytes = 0L
    private var currentPlaybackTransferredBytes = 0L

    private val playerIsInLegalState = AtomicBoolean(false)

    override fun playRemote(httpClient: OkHttpClient, streamUrl: String, context: Context) {
        if (streamUrl != this.streamUrl) currentPlaybackTransferredBytes = 0
        this.streamUrl = streamUrl
        this.context = context

        Log.v(TAG, "Stream url:$streamUrl")
        isHls = Utils.urlIndicatesHlsStream(streamUrl)

        if (!isHls) {
            if (proxy != null) {
                if (BuildConfig.DEBUG) Log.d(TAG, "stopping old proxy.")
                stopProxy()
            }
            proxy = StreamProxy(httpClient, streamUrl, this)
        } else {
            stopProxy()
            onStreamCreated(streamUrl)
        }
    }

    private fun playProxyStream(proxyUrl: String, context: Context) {
        playerIsInLegalState.set(false)

        if (mediaPlayer == null) mediaPlayer = MediaPlayer()

        if (mediaPlayer!!.isPlaying) mediaPlayer!!.stop()
        mediaPlayer!!.reset()

        try {
            @Suppress("DEPRECATION")
            mediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
            mediaPlayer!!.setDataSource(proxyUrl)
            mediaPlayer!!.prepareAsync()

            mediaPlayer!!.setOnPreparedListener {
                playerIsInLegalState.set(true)
                stateListener.onStateChanged(PlayState.PrePlaying)
                mediaPlayer!!.start()
                stateListener.onStateChanged(PlayState.Playing)
            }

            mediaPlayer!!.setOnErrorListener { _, _, _ ->
                stateListener.onPlayerError(R.string.error_play_stream)
                true
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "$e")
            stateListener.onPlayerError(R.string.error_stream_url)
        } catch (e: IOException) {
            Log.e(TAG, "$e")
            stateListener.onPlayerError(R.string.error_caching_stream)
        } catch (e: Exception) {
            Log.e(TAG, "$e")
            stateListener.onPlayerError(R.string.error_play_stream)
        }
    }

    override fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
                it.reset()
                stateListener.onStateChanged(PlayState.Paused)
            } else {
                stop()
            }
        }
        stopProxy()
    }

    override fun stop() {
        mediaPlayer?.let {
            playerIsInLegalState.set(false)
            if (it.isPlaying) it.stop()
            it.release()
            mediaPlayer = null
            playerIsInLegalState.set(true)
        }
        stateListener.onStateChanged(PlayState.Idle)
        stopProxy()
    }

    override fun isPlaying(): Boolean {
        val mp = mediaPlayer ?: return false
        return !playerIsInLegalState.get() || mp.isPlaying
    }

    override fun getBufferedMs() = -1L

    override fun getAudioSessionId() = mediaPlayer?.audioSessionId ?: 0

    override fun getTotalTransferredBytes() = totalTransferredBytes

    override fun getCurrentPlaybackTransferredBytes() = currentPlaybackTransferredBytes

    override fun isLocal() = true

    override fun setVolume(newVolume: Float) {
        mediaPlayer?.setVolume(newVolume, newVolume)
    }

    override fun setStateListener(listener: PlayerWrapper.PlayListener) {
        stateListener = listener
    }

    override fun onFoundShoutcastStream(shoutcastInfo: ShoutcastInfo, isHls: Boolean) {
        stateListener.onDataSourceShoutcastInfo(shoutcastInfo, isHls)
    }

    override fun onFoundLiveStreamInfo(liveInfo: StreamLiveInfo) {
        stateListener.onDataSourceStreamLiveInfo(liveInfo)
    }

    override fun onStreamCreated(proxyConnection: String) {
        playerThreadHandler.post { playProxyStream(proxyConnection, context!!) }
    }

    override fun onStreamStopped() { stop() }

    override fun onBytesRead(buffer: ByteArray, offset: Int, length: Int) {
        totalTransferredBytes += length
        currentPlaybackTransferredBytes += length
    }

    private fun stopProxy() {
        proxy?.let {
            try { it.stop() } catch (e: Exception) { Log.e(TAG, "proxy stop exception: ", e) }
            proxy = null
        }
    }
}
