package com.github.fschroffner.radiodroid3.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.paging.PagedList
import com.github.fschroffner.radiodroid3.RadioDroidApp

class TrackHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TrackHistoryRepository = (getApplication<Application>() as RadioDroidApp).trackHistoryRepository

    fun getAllHistoryPaged(): LiveData<PagedList<TrackHistoryEntry>> = repository.allHistoryPaged
}
