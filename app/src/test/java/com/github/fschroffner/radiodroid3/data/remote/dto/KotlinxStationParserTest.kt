package com.github.fschroffner.radiodroid3.data.remote.dto

import com.github.fschroffner.radiodroid3.data.mapper.toDomain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KotlinxStationParserTest {

    private val jsonPayload = """
        [
          {
            "stationuuid": "kotlinx-uuid-001",
            "name": "Electronic Beats",
            "url": "https://stream.electronic.fm/live",
            "homepage": "https://electronic.fm",
            "favicon": "https://electronic.fm/logo.png",
            "country": "Germany",
            "countrycode": "DE",
            "tags": "electronic,techno,house",
            "bitrate": 320,
            "codec": "MP3",
            "votes": 1250,
            "clickcount": 5400,
            "lastcheckok": 1,
            "hls": 0,
            "unknown_future_api_field": "safe_to_ignore"
          }
        ]
    """.trimIndent()

    @Test
    fun testParseListWithKotlinxSerialization() {
        val dtos = KotlinxStationParser.parseList(jsonPayload)
        assertEquals(1, dtos.size)

        val dto = dtos[0]
        assertEquals("kotlinx-uuid-001", dto.stationUuid)
        assertEquals("Electronic Beats", dto.name)
        assertEquals("https://stream.electronic.fm/live", dto.streamUrl)
        assertEquals("DE", dto.countryCode)
        assertEquals(320, dto.bitrate)
        assertEquals(1250, dto.votes)

        val domain = dto.toDomain()
        assertEquals("kotlinx-uuid-001", domain.stationUuid)
        assertEquals("Electronic Beats", domain.name)
        assertTrue(domain.working)
        assertTrue(domain.hasIcon)
    }

    @Test
    fun testParseSingleStation() {
        val singleJson = """
            {
              "stationuuid": "single-uuid-77",
              "name": "Single Station",
              "url": "http://single.stream"
            }
        """.trimIndent()

        val dto = KotlinxStationParser.parseStation(singleJson)
        assertNotNull(dto)
        assertEquals("single-uuid-77", dto?.stationUuid)
        assertEquals("Single Station", dto?.name)
    }

    @Test
    fun testEmptyAndMalformedJsonGracefulFallback() {
        assertTrue(KotlinxStationParser.parseList("").isEmpty())
        assertTrue(KotlinxStationParser.parseList("[]").isEmpty())
        assertTrue(KotlinxStationParser.parseList("invalid-not-json").isEmpty())
        assertNull(KotlinxStationParser.parseStation("not-json"))
    }
}
