package com.github.fschroffner.floradio.history

import android.app.Application
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.github.fschroffner.floradio.database.FloRadioDatabase
import com.github.fschroffner.floradio.history.TrackHistoryEntry.Companion.MAX_HISTORY_ITEMS_IN_TABLE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackHistoryRepository @Inject constructor(
    private val database: FloRadioDatabase,
    private val dao: TrackHistoryDao
) {
    fun interface GetItemCallback {
        /** Runs in the DB thread */
        fun onItemFetched(trackHistoryEntry: TrackHistoryEntry?, dao: TrackHistoryDao)
    }

    private val queryExecutor: Executor = database.historyQueryExecutor
    val allHistoryFlow: Flow<PagingData<TrackHistoryEntry>> = Pager(
        config = PagingConfig(
            pageSize = HISTORY_PAGE_SIZE,
            enablePlaceholders = true
        ),
        pagingSourceFactory = dao.getAllHistoryPositional().asPagingSourceFactory(Dispatchers.IO)
    ).flow

    private var insertsToTruncateLeft = 0

    constructor(application: Application) : this(
        FloRadioDatabase.getDatabase(application),
        FloRadioDatabase.getDatabase(application).songHistoryDao()
    )

    fun insert(historyEntry: TrackHistoryEntry) {
        queryExecutor.execute {
            dao.insert(historyEntry)
            if (insertsToTruncateLeft == 0) {
                insertsToTruncateLeft = TRUNCATE_FREQUENCY
                dao.truncateHistory(MAX_HISTORY_ITEMS_IN_TABLE)
            } else {
                insertsToTruncateLeft--
            }
        }
    }

    fun setCurrentPlayingTrackEndTime(time: Date) {
        queryExecutor.execute { dao.setCurrentPlayingTrackEndTime(time) }
    }

    fun setLastHistoryItemEndTimeRelative(deltaSeconds: Int) {
        queryExecutor.execute { dao.setLastHistoryItemEndTimeRelative(deltaSeconds) }
    }

    fun setTrackArtUrl(id: Int, artUrl: String) {
        queryExecutor.execute { dao.setTrackArtUrl(id, artUrl) }
    }

    fun getLastInsertedHistoryItem(callback: GetItemCallback) {
        queryExecutor.execute {
            val item = dao.getLastInsertedHistoryItem()
            callback.onItemFetched(item, dao)
        }
    }

    fun deleteHistory() {
        queryExecutor.execute { dao.deleteHistory() }
    }

    companion object {
        private const val HISTORY_PAGE_SIZE = 15
        private const val TRUNCATE_FREQUENCY = 20
    }
}