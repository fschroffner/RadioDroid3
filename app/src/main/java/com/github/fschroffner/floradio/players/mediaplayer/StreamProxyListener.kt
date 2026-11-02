package com.github.fschroffner.floradio.players.mediaplayer

import com.github.fschroffner.floradio.station.live.ShoutcastInfo
import com.github.fschroffner.floradio.station.live.StreamLiveInfo

interface StreamProxyListener {
    fun onFoundShoutcastStream(bitrate: ShoutcastInfo, isHls: Boolean)
    fun onFoundLiveStreamInfo(liveInfo: StreamLiveInfo)
    fun onStreamCreated(proxyConnection: String)
    fun onStreamStopped()
    fun onBytesRead(buffer: ByteArray, offset: Int, length: Int)
}
