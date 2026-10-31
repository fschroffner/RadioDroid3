package com.github.fschroffner.radiodroid3.data.remote.dto

import kotlinx.serialization.json.Json

/**
 * Modern compile-time, reflection-free JSON parser using [kotlinx.serialization].
 * Replaces reflection-heavy Gson with better performance and strict nullability checks.
 */
object KotlinxStationParser {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parseList(jsonString: String): List<StationDto> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<StationDto>>(jsonString)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseStation(jsonString: String): StationDto? {
        if (jsonString.isBlank()) return null
        return try {
            json.decodeFromString<StationDto>(jsonString)
        } catch (_: Exception) {
            null
        }
    }
}
