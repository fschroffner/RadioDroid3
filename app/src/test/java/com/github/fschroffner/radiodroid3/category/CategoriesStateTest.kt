package com.github.fschroffner.radiodroid3.category

import com.github.fschroffner.radiodroid3.data.DataCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CategoriesStateTest {

    private val sampleJson = """
        [
          {"name": "jazz", "stationcount": 1500},
          {"name": "rock", "stationcount": 3200},
          {"name": "classical", "stationcount": 800}
        ]
    """.trimIndent()

    @Test
    fun testDataCategoryJsonDecoding() {
        val categories = DataCategory.DecodeJson(sampleJson)
        assertEquals(3, categories.size)

        assertEquals("jazz", categories[0].Name)
        assertEquals(1500, categories[0].UsedCount)

        assertEquals("rock", categories[1].Name)
        assertEquals(3200, categories[1].UsedCount)

        assertEquals("classical", categories[2].Name)
        assertEquals(800, categories[2].UsedCount)
    }

    @Test
    fun testCategoriesUiStateHierarchy() {
        val idle: CategoriesUiState = CategoriesUiState.Idle
        val loading: CategoriesUiState = CategoriesUiState.Loading
        val error: CategoriesUiState = CategoriesUiState.Error

        val list = listOf(DataCategory().apply {
            Name = "pop"
            UsedCount = 42
        })
        val success: CategoriesUiState = CategoriesUiState.Success(list)

        assertTrue(idle is CategoriesUiState.Idle)
        assertTrue(loading is CategoriesUiState.Loading)
        assertTrue(error is CategoriesUiState.Error)
        assertTrue(success is CategoriesUiState.Success)
        assertEquals(1, (success as CategoriesUiState.Success).categories.size)
        assertEquals("pop", success.categories[0].Name)
    }
}
