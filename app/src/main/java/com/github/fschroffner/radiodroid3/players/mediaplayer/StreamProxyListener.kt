package com.github.fschroffner.radiodroid3.players.mediaplayer

import com.github.fschroffner.radiodroid3.station.live.ShoutcastInfo
import com.github.fschroffner.radiodroid3.station.live.StreamLiveInfo

interface StreamProxyListener {
    fun onFoundShoutcastStream(bitrate: ShoutcastInfo, isHls: Boolean)
    fun onFoundLiveStreamInfo(liveInfo: StreamLiveInfo)
    fun onStreamCreated(proxyConnection: String)
    fun onStreamStopped()
    fun onBytesRead(buffer: ByteArray, offset: Int, length: Int)
}
