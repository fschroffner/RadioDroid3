package com.github.fschroffner.radiodroid3.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.paging.PagedList
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TrackHistoryViewModel @Inject constructor(
    application: Application,
    private val repository: TrackHistoryRepository
) : AndroidViewModel(application) {

    fun getAllHistoryPaged(): LiveData<PagedList<TrackHistoryEntry>> = repository.allHistoryPaged
}
