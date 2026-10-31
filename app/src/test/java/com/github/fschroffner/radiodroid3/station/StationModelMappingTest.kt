package com.github.fschroffner.radiodroid3.station

import com.github.fschroffner.radiodroid3.data.mapper.toDataRadioStation
import com.github.fschroffner.radiodroid3.data.mapper.toDomain
import com.github.fschroffner.radiodroid3.data.mapper.toDto
import com.github.fschroffner.radiodroid3.data.mapper.toEntity
import com.github.fschroffner.radiodroid3.data.remote.dto.StationDtoParser
import com.github.fschroffner.radiodroid3.domain.model.RadioStation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StationModelMappingTest {

    private val sampleJson = """
        [
          {
            "stationuuid": "1111-2222-3333",
            "name": "Jazz Radio One",
            "url": "https://stream.jazzradio.com/live",
            "homepage": "https://jazzradio.com",
            "favicon": "https://jazzradio.com/logo.png",
            "country": "Germany",
            "countrycode": "DE",
            "state": "Berlin",
            "tags": "jazz,smooth,blues",
            "language": "german",
            "votes": 420,
            "clickcount": 1337,
            "clicktrend": 5,
            "bitrate": 192,
            "codec": "MP3",
            "lastcheckok": 1,
            "hls": 0,
            "DeletedOnServer": 0
          }
        ]
    """.trimIndent()

    @Test
    fun testDtoParserAndDomainMapping() {
        val dtos = StationDtoParser.parseList(sampleJson)
        assertEquals(1, dtos.size)

        val dto = dtos[0]
        assertEquals("1111-2222-3333", dto.stationUuid)
        assertEquals("Jazz Radio One", dto.name)
        assertEquals("https://stream.jazzradio.com/live", dto.streamUrl)
        assertEquals(420, dto.votes)
        assertEquals(192, dto.bitrate)

        val domain: RadioStation = dto.toDomain()
        assertEquals("1111-2222-3333", domain.stationUuid)
        assertEquals("Jazz Radio One", domain.name)
        assertEquals("https://stream.jazzradio.com/live", domain.streamUrl)
        assertEquals("Germany", domain.country)
        assertEquals("DE", domain.countryCode)
        assertEquals("jazz,smooth,blues", domain.tagsAll)
        assertTrue(domain.working)
        assertTrue(domain.hasIcon)
        assertTrue(domain.hasValidUuid)
    }

    @Test
    fun testDomainToEntityAndBack() {
        val domain = RadioStation(
            stationUuid = "abc-123",
            name = "Rock Antenne",
            streamUrl = "https://rock.stream",
            isFavorite = true,
            favoriteOrder = 1,
            lastPlayed = 1700000000000L
        )

        val entity = domain.toEntity()
        assertEquals("abc-123", entity.stationUuid)
        assertEquals("Rock Antenne", entity.name)
        assertEquals("https://rock.stream", entity.streamUrl)
        assertTrue(entity.isFavorite)
        assertEquals(1, entity.favoriteOrder)
        assertEquals(1700000000000L, entity.lastPlayed)

        val restoredDomain = entity.toDomain()
        assertEquals(domain.stationUuid, restoredDomain.stationUuid)
        assertEquals(domain.name, restoredDomain.name)
        assertEquals(domain.isFavorite, restoredDomain.isFavorite)
        assertEquals(domain.favoriteOrder, restoredDomain.favoriteOrder)
        assertEquals(domain.lastPlayed, restoredDomain.lastPlayed)
    }

    @Test
    fun testLegacyDataRadioStationBridge() {
        val stations = DataRadioStation.DecodeJson(sampleJson)
        assertEquals(1, stations.size)

        val station = stations[0]
        // Check camelCase accessors
        assertEquals("1111-2222-3333", station.stationUuid)
        assertEquals("Jazz Radio One", station.name)
        assertEquals("https://stream.jazzradio.com/live", station.streamUrl)

        // Check PascalCase compatibility accessors
        @Suppress("DEPRECATION")
        run {
            assertEquals("1111-2222-3333", station.StationUuid)
            assertEquals("Jazz Radio One", station.Name)
            assertEquals("https://stream.jazzradio.com/live", station.StreamUrl)
        }

        // Check toJson()
        val json = station.toJson()
        assertNotNull(json)
        assertEquals("1111-2222-3333", json!!.getString("stationuuid"))
        assertEquals("Jazz Radio One", json.getString("name"))

        // Check single decode
        val singleJson = json.toString()
        val singleStation = DataRadioStation.DecodeJsonSingle(singleJson)
        assertNotNull(singleStation)
        assertEquals("1111-2222-3333", singleStation!!.stationUuid)
    }
}
