package net.programmierecke.radiodroid2.tests.utils.http

import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import java.io.IOException

object IcyStreamGenerator {

    fun generateIcyStream(clazz: Class<*>, sourceFile: String): MockResponse {
        val inputStream = clazz.getResourceAsStream(sourceFile)
        try {
            return MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mpeg")
                .setBody(Buffer().readFrom(inputStream!!))
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }
}
