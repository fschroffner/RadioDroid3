package com.github.fschroffner.radiodroid3.station

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.fschroffner.radiodroid3.HistoryManager
import com.github.fschroffner.radiodroid3.Utils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * UI State for the playback history screen.
 */
data class HistoryUiState(
    val stations: List<DataRadioStation> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel managing station history with Unidirectional Data Flow (UDF).
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyManager: HistoryManager,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        historyManager.stationsFlow,
        _isRefreshing,
        _errorMessage
    ) { stations, isRefreshing, errorMessage ->
        HistoryUiState(
            stations = stations,
            isRefreshing = isRefreshing,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    fun refresh() {
        val uuids = historyManager.listStations.map { it.StationUuid }
        if (uuids.isEmpty()) {
            _isRefreshing.value = false
            return
        }

        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            try {
                val updatedStations = withContext(Dispatchers.IO) {
                    Utils.getStationsByUuid(okHttpClient, context, uuids)
                }
                if (updatedStations != null) {
                    historyManager.replaceList(updatedStations)
                } else {
                    _errorMessage.value = "Failed to refresh history"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun removeStation(station: DataRadioStation): Int {
        return historyManager.remove(station.StationUuid)
    }

    fun restoreStation(station: DataRadioStation, pos: Int) {
        historyManager.restore(station, pos)
    }

    fun clearHistory() {
        historyManager.clear()
    }
}
