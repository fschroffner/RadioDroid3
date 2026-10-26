package com.github.fschroffner.radiodroid3.player

import com.github.fschroffner.radiodroid3.players.PlayState
import com.github.fschroffner.radiodroid3.recording.DataRecording
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.FavoritesUiState
import com.github.fschroffner.radiodroid3.station.HistoryUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerUiStateTest {

    @Test
    fun testDefaultPlayerUiState() {
        val state = PlayerUiState()
        assertFalse(state.isPlaying)
        assertEquals(PlayState.Idle, state.playState)
        assertNull(state.station)
        assertFalse(state.isRecording)
        assertFalse(state.isFavorite)
        assertFalse(state.isConnected)
        assertEquals(0L, state.bufferedSeconds)
        assertEquals(0L, state.transferredBytes)
        assertEquals(0L, state.sleepTimerSeconds)
        assertTrue(state.savedRecordings.isEmpty())
        assertNull(state.albumArtUrl)
    }

    @Test
    fun testPlayerUiStateUpdate() {
        val station = DataRadioStation().apply {
            StationUuid = "fav-uuid-1"
            Name = "Jazz FM"
            IconUrl = "https://example.com/icon.png"
        }
        val recording = DataRecording().apply {
            Name = "jazz_recording.mp3"
        }

        val state = PlayerUiState(
            station = station,
            isPlaying = true,
            playState = PlayState.Playing,
            isConnected = true,
            isRecording = true,
            isFavorite = true,
            bufferedSeconds = 12L,
            transferredBytes = 1048576L,
            savedRecordings = listOf(recording),
            albumArtUrl = "https://example.com/album.png"
        )

        assertTrue(state.isPlaying)
        assertTrue(state.isRecording)
        assertTrue(state.isFavorite)
        assertEquals("Jazz FM", state.station?.Name)
        assertEquals("https://example.com/album.png", state.albumArtUrl)
        assertEquals(1, state.savedRecordings.size)
        assertEquals("jazz_recording.mp3", state.savedRecordings[0].Name)
    }

    @Test
    fun testFavoritesUiState() {
        val defaultState = FavoritesUiState()
        assertTrue(defaultState.stations.isEmpty())
        assertFalse(defaultState.isRefreshing)
        assertNull(defaultState.errorMessage)

        val station = DataRadioStation().apply {
            StationUuid = "fav-1"
            Name = "Rock Antenne"
        }
        val populated = FavoritesUiState(
            stations = listOf(station),
            isRefreshing = true,
            errorMessage = null
        )
        assertEquals(1, populated.stations.size)
        assertTrue(populated.isRefreshing)
        assertEquals("Rock Antenne", populated.stations[0].Name)
    }

    @Test
    fun testHistoryUiState() {
        val defaultState = HistoryUiState()
        assertTrue(defaultState.stations.isEmpty())
        assertFalse(defaultState.isRefreshing)
        assertNull(defaultState.errorMessage)

        val station = DataRadioStation().apply {
            StationUuid = "hist-1"
            Name = "BBC World Service"
        }
        val populated = HistoryUiState(
            stations = listOf(station),
            isRefreshing = false,
            errorMessage = "Connection timeout"
        )
        assertEquals(1, populated.stations.size)
        assertFalse(populated.isRefreshing)
        assertEquals("Connection timeout", populated.errorMessage)
    }
}
