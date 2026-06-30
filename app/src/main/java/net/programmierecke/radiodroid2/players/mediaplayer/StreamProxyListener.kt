package net.programmierecke.radiodroid2.players.mediaplayer

import net.programmierecke.radiodroid2.station.live.ShoutcastInfo
import net.programmierecke.radiodroid2.station.live.StreamLiveInfo

interface StreamProxyListener {
    fun onFoundShoutcastStream(bitrate: ShoutcastInfo, isHls: Boolean)
    fun onFoundLiveStreamInfo(liveInfo: StreamLiveInfo)
    fun onStreamCreated(proxyConnection: String)
    fun onStreamStopped()
    fun onBytesRead(buffer: ByteArray, offset: Int, length: Int)
}
