package com.github.fschroffner.floradio.players

import android.content.Context
import com.github.fschroffner.floradio.station.live.ShoutcastInfo
import com.github.fschroffner.floradio.station.live.StreamLiveInfo
import okhttp3.OkHttpClient

interface PlayerWrapper {
    interface PlayListener {
        fun onStateChanged(state: PlayState)
        fun onPlayerWarning(messageId: Int)
        fun onPlayerError(messageId: Int)
        fun onDataSourceShoutcastInfo(shoutcastInfo: ShoutcastInfo?, isHls: Boolean)
        fun onDataSourceStreamLiveInfo(liveInfo: StreamLiveInfo)
    }

    fun playRemote(httpClient: OkHttpClient, streamUrl: String, context: Context)
    fun pause()
    fun stop()
    fun isPlaying(): Boolean
    fun getBufferedMs(): Long
    fun getAudioSessionId(): Int
    fun getTotalTransferredBytes(): Long
    fun getCurrentPlaybackTransferredBytes(): Long
    fun isLocal(): Boolean
    fun setVolume(newVolume: Float)
    fun setStateListener(listener: PlayListener)
}
