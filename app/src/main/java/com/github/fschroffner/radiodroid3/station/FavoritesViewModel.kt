package com.github.fschroffner.radiodroid3.station

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.fschroffner.radiodroid3.FavouriteManager
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.data.mapper.toDomain
import com.github.fschroffner.radiodroid3.domain.model.RadioStation
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
 * UI State for the Favorites (Starred) screen.
 */
data class FavoritesUiState(
    val stations: List<DataRadioStation> = emptyList(),
    val domainStations: List<RadioStation> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel managing favorite stations with Unidirectional Data Flow (UDF).
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favouriteManager: FavouriteManager,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FavoritesUiState> = combine(
        favouriteManager.stationsFlow,
        _isRefreshing,
        _errorMessage
    ) { stations, isRefreshing, errorMessage ->
        FavoritesUiState(
            stations = stations,
            domainStations = stations.map { it.toDomain() },
            isRefreshing = isRefreshing,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FavoritesUiState()
    )

    fun refresh() {
        val uuids = favouriteManager.listStations.map { it.StationUuid }
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
                    favouriteManager.replaceList(updatedStations)
                } else {
                    _errorMessage.value = "Failed to refresh favorites"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun removeFavorite(station: DataRadioStation): Int {
        return favouriteManager.remove(station.StationUuid)
    }

    fun restoreFavorite(station: DataRadioStation, pos: Int) {
        favouriteManager.restore(station, pos)
    }

    fun moveStation(from: Int, to: Int) {
        favouriteManager.moveWithoutNotify(from, to)
    }

    fun saveOrder() {
        favouriteManager.saveOrderToDatabase()
    }
}
