package com.github.fschroffner.floradio.player

import com.github.fschroffner.floradio.data.mapper.toDomain
import com.github.fschroffner.floradio.domain.model.RadioStation
import com.github.fschroffner.floradio.players.PlayState
import com.github.fschroffner.floradio.service.PauseReason
import com.github.fschroffner.floradio.station.DataRadioStation
import com.github.fschroffner.floradio.station.live.ShoutcastInfo
import com.github.fschroffner.floradio.station.live.StreamLiveInfo

data class PlaybackState(
    val isPlaying: Boolean = false,
    val playState: PlayState = PlayState.Idle,
    val station: DataRadioStation? = null,
    val streamLiveInfo: StreamLiveInfo = StreamLiveInfo(null),
    val shoutcastInfo: ShoutcastInfo? = null,
    val isHls: Boolean = false,
    val transferredBytes: Long = 0L,
    val bufferedSeconds: Long = 0L,
    val sleepTimerSeconds: Long = 0L,
    val pauseReason: PauseReason = PauseReason.NONE,
    val isNotificationActive: Boolean = false,
    val lastPlayStartTime: Long = 0L,
    val isConnected: Boolean = false
) {
    val domainStation: RadioStation?
        get() = station?.toDomain()
}
