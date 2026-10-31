package com.github.fschroffner.radiodroid3.data.remote

import com.github.fschroffner.radiodroid3.data.remote.dto.StationDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Type-safe declarative REST API definition for the RadioBrowser backend.
 * Evaluated and integrated to replace manual OkHttp string calls and custom URL builders.
 */
interface RadioBrowserApi {

    @GET("json/stations/byuuid/{uuid}")
    suspend fun getStationByUuid(
        @Path("uuid") uuid: String
    ): List<StationDto>

    @GET("json/stations/byname/{name}")
    suspend fun getStationsByName(
        @Path("name") name: String,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("limit") limit: Int = 100
    ): List<StationDto>

    @GET("json/stations/{endpoint}")
    suspend fun getStations(
        @Path("endpoint") endpoint: String,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("limit") limit: Int? = null
    ): List<StationDto>

    @GET("json/tags")
    suspend fun getTags(
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<Map<String, Any>>

    @GET("json/countries")
    suspend fun getCountries(
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<Map<String, Any>>

    @GET("json/languages")
    suspend fun getLanguages(
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<Map<String, Any>>
}
