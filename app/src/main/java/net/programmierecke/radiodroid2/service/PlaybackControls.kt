package net.programmierecke.radiodroid2.service

import net.programmierecke.radiodroid2.station.DataRadioStation

/**
 * In-process playback operations exposed by [PlayerService].
 *
 * During the Media3 migration this replaces the AIDL [net.programmierecke.radiodroid2.IPlayerService]
 * binder for same-process collaborators (such as [MediaSessionCallback]) that used to reach the
 * service through it. Cross-process clients keep going through the MediaController / session, while
 * these in-process callers talk to the service directly.
 */
interface PlaybackControls {
    fun isPlaying(): Boolean
    fun setStation(station: DataRadioStation)
    fun play(isAlarm: Boolean)
    fun pause(pauseReason: PauseReason)
    fun resume()
    fun stop()
    fun skipToNext()
    fun skipToPrevious()
}
