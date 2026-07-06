package com.github.fschroffner.radiodroid3.tests.utils.http

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.util.Scanner

class MockHttpDispatcher : Dispatcher() {

    fun interface CustomRequestDispatcher {
        fun dispatch(path: String): MockResponse?
    }

    fun interface PathFilter {
        fun compatible(path: String): Boolean
    }

    private var customRequestDispatcher: CustomRequestDispatcher? = null

    private fun fromFile(path: String): String {
        val inputStream = javaClass.getResourceAsStream(path)
        val sc = Scanner(inputStream)
        val sb = StringBuilder()
        while (sc.hasNext()) {
            sb.append(sc.nextLine())
        }

        return sb.toString()
    }

    fun setCustomRequestDispatcher(customRequestDispatcher: CustomRequestDispatcher?) {
        this.customRequestDispatcher = customRequestDispatcher
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val originalUrlStr = request.requestUrl!!.queryParameter("url")
        val originalUrl = originalUrlStr!!.toHttpUrlOrNull()
        val path = originalUrl!!.encodedPath

        customRequestDispatcher?.let {
            val customResponse = it.dispatch(path)
            if (customResponse != null) {
                return customResponse
            }
        }

        return when {
            isStationsSearchRequest.compatible(path) ->
                MockResponse().setResponseCode(200).setBody(fromFile("/stations_search_list.json"))
            isStationsRequest.compatible(path) ->
                MockResponse().setResponseCode(200).setBody(fromFile("/stations_list.json"))
            isTagsRequest.compatible(path) ->
                MockResponse().setResponseCode(200).setBody(fromFile("/tags_list.json"))
            isCountryCodesRequest.compatible(path) ->
                MockResponse().setResponseCode(200).setBody(fromFile("/countrycodes_list.json"))
            isLanguagesRequest.compatible(path) ->
                MockResponse().setResponseCode(200).setBody(fromFile("/languages_list.json"))
            isStationUrlRequest.compatible(path) ->
                MockResponse().setResponseCode(200).setBody(fromFile("/station_url.json"))
            isAudioRequest.compatible(path) ->
                IcyStreamGenerator.generateIcyStream(javaClass, "/test.mp3")
            else -> {
                Log.w("MockHttpDispatcher", String.format("No handling of \"%s\"", request.toString()))
                MockResponse().setResponseCode(404)
            }
        }
    }

    companion object {
        val isStationsRequest = PathFilter { path -> path.startsWith("/json/stations") }
        val isStationsSearchRequest = PathFilter { path -> path.startsWith("/json/stations/by") }
        val isTagsRequest = PathFilter { path -> path.startsWith("/json/tags") }
        val isCountryCodesRequest = PathFilter { path -> path.startsWith("/json/countrycodes") }
        val isLanguagesRequest = PathFilter { path -> path.startsWith("/json/languages") }
        val isStationUrlRequest = PathFilter { path -> path.startsWith("/json/url") }
        val isAudioRequest = PathFilter { path -> path.endsWith("audio.mp3") }
    }
}
