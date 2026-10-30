package com.github.fschroffner.radiodroid3.player

import com.github.fschroffner.radiodroid3.players.PlayState
import com.github.fschroffner.radiodroid3.service.PauseReason
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackStateTest {

    @Test
    fun testDefaultPlaybackState() {
        val state = PlaybackState()
        assertFalse(state.isPlaying)
        assertEquals(PlayState.Idle, state.playState)
        assertNull(state.station)
        assertEquals(PauseReason.NONE, state.pauseReason)
        assertEquals(0L, state.transferredBytes)
        assertEquals(0L, state.bufferedSeconds)
        assertEquals(0L, state.sleepTimerSeconds)
        assertFalse(state.isConnected)
    }

    @Test
    fun testPlaybackStateCopy() {
        val station = DataRadioStation().apply {
            StationUuid = "uuid-test"
            Name = "Test Station"
        }
        val state = PlaybackState(
            isPlaying = true,
            playState = PlayState.Playing,
            station = station,
            sleepTimerSeconds = 300L
        )

        assertTrue(state.isPlaying)
        assertEquals(PlayState.Playing, state.playState)
        assertEquals("Test Station", state.station?.Name)
        assertEquals(300L, state.sleepTimerSeconds)

        val paused = state.copy(isPlaying = false, playState = PlayState.Paused, pauseReason = PauseReason.USER)
        assertFalse(paused.isPlaying)
        assertEquals(PlayState.Paused, paused.playState)
        assertEquals(PauseReason.USER, paused.pauseReason)
        assertEquals("Test Station", paused.station?.Name)
    }
}
