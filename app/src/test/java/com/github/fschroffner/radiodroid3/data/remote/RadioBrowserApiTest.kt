package com.github.fschroffner.radiodroid3.data.remote

import com.github.fschroffner.radiodroid3.data.mapper.toDomain
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RadioBrowserApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: RadioBrowserApi

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(RadioBrowserApi::class.java)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testGetStationByUuid() = runTest {
        val json = """
            [
              {
                "stationuuid": "mock-uuid-123",
                "name": "Retrofit FM",
                "url": "https://stream.mock.com",
                "bitrate": 320,
                "codec": "MP3",
                "votes": 999
              }
            ]
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val result = api.getStationByUuid("mock-uuid-123")
        assertEquals(1, result.size)
        assertEquals("mock-uuid-123", result[0].stationUuid)
        assertEquals("Retrofit FM", result[0].name)
        assertEquals(320, result[0].bitrate)

        val domain = result[0].toDomain()
        assertEquals("Retrofit FM", domain.name)
        assertEquals(999, domain.votes)

        val recordedRequest = server.takeRequest()
        assertEquals("/json/stations/byuuid/mock-uuid-123", recordedRequest.path)
    }

    @Test
    fun testGetStationsByName() = runTest {
        val json = """
            [
              {"stationuuid": "uuid-1", "name": "Jazz Berlin"},
              {"stationuuid": "uuid-2", "name": "Jazz Munich"}
            ]
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val result = api.getStationsByName("jazz", hideBroken = true, limit = 50)
        assertEquals(2, result.size)
        assertEquals("Jazz Berlin", result[0].name)
        assertEquals("Jazz Munich", result[1].name)

        val recordedRequest = server.takeRequest()
        assertTrue(recordedRequest.path!!.startsWith("/json/stations/byname/jazz"))
        assertTrue(recordedRequest.path!!.contains("hidebroken=true"))
        assertTrue(recordedRequest.path!!.contains("limit=50"))
    }
}
