package net.programmierecke.radiodroid2.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.paging.PagedList
import net.programmierecke.radiodroid2.RadioDroidApp

class TrackHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TrackHistoryRepository = (getApplication<Application>() as RadioDroidApp).trackHistoryRepository

    fun getAllHistoryPaged(): LiveData<PagedList<TrackHistoryEntry>> = repository.allHistoryPaged
}
