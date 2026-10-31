package com.github.fschroffner.radiodroid3.data.remote.dto

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Modern JSON parser for RadioBrowser station endpoints.
 * Replaces legacy manual org.json parsing with type-safe Gson parsing.
 */
object StationDtoParser {
    private const val TAG = "StationDtoParser"
    val gson: Gson = Gson()
    private val listType = object : TypeToken<List<StationDto>>() {}.type

    fun parseList(json: String?): List<StationDto> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<StationDto>>(json, listType) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse station list: $e")
            emptyList()
        }
    }

    fun parseSingle(json: String?): StationDto? {
        if (json.isNullOrBlank()) return null
        return try {
            gson.fromJson(json, StationDto::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse single station: $e")
            null
        }
    }

    fun toJson(dto: StationDto): String {
        return gson.toJson(dto)
    }

    fun toJsonList(dtos: List<StationDto>): String {
        return gson.toJson(dtos)
    }
}
