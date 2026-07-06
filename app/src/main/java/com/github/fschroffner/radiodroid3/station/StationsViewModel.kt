package com.github.fschroffner.radiodroid3.station

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.github.fschroffner.radiodroid3.data.StationRepository
import javax.inject.Inject

/**
 * UI state for a remote station list screen. A single immutable value the view renders.
 */
sealed interface StationsUiState {
    object Idle : StationsUiState
    object Loading : StationsUiState
    data class Success(val stations: List<DataRadioStation>) : StationsUiState
    object Error : StationsUiState
}

/**
 * Holds the station list state for a browse tab and survives configuration changes.
 * Replaces the per-fragment [android.os.AsyncTask] download in `FragmentBase`.
 */
@HiltViewModel
class StationsViewModel @Inject constructor(
    private val stationRepository: StationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StationsUiState>(StationsUiState.Idle)
    val uiState: StateFlow<StationsUiState> = _uiState.asStateFlow()

    fun load(relativeUrl: String, hideBroken: Boolean, forceUpdate: Boolean) {
        if (relativeUrl.isEmpty()) return
        _uiState.value = StationsUiState.Loading
        viewModelScope.launch {
            val stations = stationRepository.getStations(relativeUrl, hideBroken, forceUpdate)
            _uiState.value =
                if (stations == null) StationsUiState.Error else StationsUiState.Success(stations)
        }
    }
}
