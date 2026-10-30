package com.github.fschroffner.radiodroid3.player

import com.github.fschroffner.radiodroid3.players.PlayState
import com.github.fschroffner.radiodroid3.service.PauseReason
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.live.ShoutcastInfo
import com.github.fschroffner.radiodroid3.station.live.StreamLiveInfo

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
)
