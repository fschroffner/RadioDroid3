package com.github.fschroffner.radiodroid3.data

import com.github.fschroffner.radiodroid3.data.mapper.toDomain
import com.github.fschroffner.radiodroid3.data.remote.dto.StationDtoParser
import com.github.fschroffner.radiodroid3.domain.model.RadioStation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StationDtoParsingComprehensiveTest {

    @Test
    fun testEmptyAndBlankJson() {
        assertTrue(StationDtoParser.parseList("").isEmpty())
        assertTrue(StationDtoParser.parseList("[]").isEmpty())
        assertTrue(StationDtoParser.parseList("   ").isEmpty())
    }

    @Test
    fun testParseStationWithHlsAndLastCheck() {
        val json = """
            [
              {
                "stationuuid": "hls-uuid-99",
                "name": "Live HLS Stream",
                "url": "https://stream.example.com/playlist.m3u8",
                "hls": 1,
                "lastcheckok": 1,
                "bitrate": 256,
                "codec": "AAC",
                "votes": 55,
                "clickcount": 120,
                "clicktrend": -1
              }
            ]
        """.trimIndent()

        val list = StationDtoParser.parseList(json)
        assertEquals(1, list.size)

        val station: RadioStation = list[0].toDomain()
        assertEquals("hls-uuid-99", station.stationUuid)
        assertEquals("Live HLS Stream", station.name)
        assertTrue(station.hls)
        assertTrue(station.working)
        assertEquals(256, station.bitrate)
        assertEquals("AAC", station.codec)
        assertEquals(55, station.votes)
        assertEquals(120, station.clickCount)
        assertEquals(-1, station.clickTrend)
    }

    @Test
    fun testFallbackToLegacyIdWhenUuidEmpty() {
        val json = """
            [
              {
                "id": "legacy-id-777",
                "name": "Old School Radio",
                "url": "http://legacy.stream"
              }
            ]
        """.trimIndent()

        val list = StationDtoParser.parseList(json)
        assertEquals(1, list.size)
        val domain = list[0].toDomain()
        assertEquals("legacy-id-777", domain.stationUuid)
        assertEquals("Old School Radio", domain.name)
    }

    @Test
    fun testDomainModelImmutabilityHelpers() {
        val original = RadioStation(
            stationUuid = "immutable-test",
            name = "Test Radio",
            streamUrl = "http://radio.test"
        )

        val updatedUrl = original.withPlayableUrl("http://resolved.test")
        assertEquals("http://resolved.test", updatedUrl.playableUrl)
        assertNull(original.playableUrl) // original untouched

        val fav = original.withFavorite(true, 5)
        assertTrue(fav.isFavorite)
        assertEquals(5, fav.favoriteOrder)
        assertFalse(original.isFavorite) // original untouched

        val played = original.withLastPlayed(999999L)
        assertEquals(999999L, played.lastPlayed)
        assertNull(original.lastPlayed) // original untouched
    }
}
