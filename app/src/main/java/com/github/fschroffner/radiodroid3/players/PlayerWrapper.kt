package com.github.fschroffner.radiodroid3.players

import android.content.Context
import com.github.fschroffner.radiodroid3.recording.Recordable
import com.github.fschroffner.radiodroid3.station.live.ShoutcastInfo
import com.github.fschroffner.radiodroid3.station.live.StreamLiveInfo
import okhttp3.OkHttpClient

interface PlayerWrapper : Recordable {
    interface PlayListener {
        fun onStateChanged(state: PlayState)
        fun onPlayerWarning(messageId: Int)
        fun onPlayerError(messageId: Int)
        fun onDataSourceShoutcastInfo(shoutcastInfo: ShoutcastInfo?, isHls: Boolean)
        fun onDataSourceStreamLiveInfo(liveInfo: StreamLiveInfo)
    }

    fun playRemote(httpClient: OkHttpClient, streamUrl: String, context: Context, isAlarm: Boolean)
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
