package com.github.fschroffner.radiodroid3

import android.content.Context
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.RadioStationDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class HistoryManager(
    ctx: Context,
    stationDao: RadioStationDao? = null
) : StationSaveManager(ctx, stationDao) {

    override fun getSaveId() = "history"

    override val stationsFlow: Flow<List<DataRadioStation>>
        get() = stationDao?.getHistoryStations() ?: emptyFlow()

    @Inject
    constructor(@ApplicationContext ctx: Context) : this(
        ctx,
        try {
            dagger.hilt.EntryPoints.get(ctx.applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java).radioStationDao()
        } catch (e: Exception) {
            null
        }
    )

    override fun add(station: DataRadioStation) {
        station.lastPlayed = System.currentTimeMillis()
        val existing = getById(station.StationUuid)
        if (existing != null) {
            existing.lastPlayed = station.lastPlayed
            listStations.remove(existing)
            listStations.add(0, existing)
            Save()
            return
        }
        cutList(MAXSIZE - 1)
        super.addFront(station)
    }

    override fun remove(id: String): Int {
        stationDao?.let { dao ->
            coroutineScope.launch {
                dao.setLastPlayed(id, 0L)
            }
        }
        return super.remove(id)
    }

    override fun clear() {
        stationDao?.let { dao ->
            coroutineScope.launch {
                dao.clearHistory()
            }
        }
        super.clear()
    }

    private fun cutList(count: Int) {
        if (listStations.size > count) {
            listStations = listStations.subList(0, count).toMutableList()
        }
    }

    companion object {
        private const val MAXSIZE = 25
    }
}
