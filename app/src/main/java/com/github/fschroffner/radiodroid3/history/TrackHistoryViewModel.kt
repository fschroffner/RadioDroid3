package com.github.fschroffner.radiodroid3.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class TrackHistoryViewModel @Inject constructor(
    application: Application,
    repository: TrackHistoryRepository
) : AndroidViewModel(application) {

    val historyFlow: Flow<PagingData<TrackHistoryEntry>> =
        repository.allHistoryFlow.cachedIn(viewModelScope)
}