package com.github.fschroffner.radiodroid3.player

import app.cash.turbine.test
import com.github.fschroffner.radiodroid3.players.PlayState
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit test verifying reactive Flow emissions for Player UI State using Turbine.
 */
class PlayerUiStateTurbineTest {

    @Test
    fun testPlaybackStateFlowTurbine() = runTest {
        val playbackFlow = MutableStateFlow(PlaybackState())

        playbackFlow.test {
            // Initial emission
            val initial = awaitItem()
            assertFalse(initial.isPlaying)
            assertEquals(PlayState.Idle, initial.playState)

            // Update to PrePlaying
            val station = DataRadioStation().apply {
                StationUuid = "test-uuid-42"
                Name = "Deep Space Radio"
            }
            playbackFlow.update {
                it.copy(
                    station = station,
                    playState = PlayState.PrePlaying,
                    isPlaying = false
                )
            }

            val buffering = awaitItem()
            assertEquals("Deep Space Radio", buffering.station?.Name)
            assertEquals(PlayState.PrePlaying, buffering.playState)
            assertFalse(buffering.isPlaying)

            // Update to Playing
            playbackFlow.update {
                it.copy(
                    playState = PlayState.Playing,
                    isPlaying = true
                )
            }

            val playing = awaitItem()
            assertEquals(PlayState.Playing, playing.playState)
            assertTrue(playing.isPlaying)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testPlayerUiStateFavoriteToggle() = runTest {
        val stateFlow = MutableStateFlow(
            PlayerUiState(
                station = DataRadioStation().apply { StationUuid = "uuid-1"; Name = "Jazz FM" },
                isPlaying = true,
                isFavorite = false
            )
        )

        stateFlow.test {
            val first = awaitItem()
            assertFalse(first.isFavorite)

            stateFlow.update { it.copy(isFavorite = true) }
            val second = awaitItem()
            assertTrue(second.isFavorite)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
