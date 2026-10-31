package com.github.fschroffner.radiodroid3.category

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.fschroffner.radiodroid3.CountryCodeDictionary
import com.github.fschroffner.radiodroid3.CountryFlagsLoader
import com.github.fschroffner.radiodroid3.data.CategoryRepository
import com.github.fschroffner.radiodroid3.data.DataCategory
import com.github.fschroffner.radiodroid3.station.StationsFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import javax.inject.Inject

/**
 * Immutable UI state for category list screens (tags, countries, languages).
 */
sealed interface CategoriesUiState {
    object Idle : CategoriesUiState
    object Loading : CategoriesUiState
    data class Success(val categories: List<DataCategory>) : CategoriesUiState
    object Error : CategoriesUiState
}

/**
 * ViewModel managing category loading, background flag/country decoration, and filtering.
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<CategoriesUiState>(CategoriesUiState.Idle)
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    fun load(
        relativeUrl: String,
        forceUpdate: Boolean,
        searchStyle: StationsFilter.SearchStyle,
        singleUseFilter: Boolean,
        showSingleUseTags: Boolean
    ) {
        if (relativeUrl.isEmpty()) return
        _uiState.value = CategoriesUiState.Loading

        viewModelScope.launch {
            val rawCategories = categoryRepository.getCategories(relativeUrl, forceUpdate)
            if (rawCategories == null) {
                _uiState.value = CategoriesUiState.Error
                return@launch
            }

            val processed = withContext(Dispatchers.Default) {
                val countryDict = CountryCodeDictionary.instance
                val flagsDict = CountryFlagsLoader.instance
                val resultList = mutableListOf<DataCategory>()

                for (cat in rawCategories) {
                    if (!singleUseFilter || showSingleUseTags || cat.UsedCount > 1) {
                        if (searchStyle == StationsFilter.SearchStyle.ByCountryCodeExact) {
                            cat.Label = countryDict.getCountryByCode(cat.Name)
                            cat.Icon = flagsDict.getFlag(context, cat.Name)
                        }
                        resultList.add(cat)
                    }
                }

                if (searchStyle == StationsFilter.SearchStyle.ByCountryCodeExact) {
                    Collections.sort(resultList)
                }
                resultList
            }

            _uiState.value = CategoriesUiState.Success(processed)
        }
    }
}
