package com.github.fschroffner.radiodroid3

import android.content.Context
import com.github.fschroffner.radiodroid3.data.mapper.toDataRadioStation
import com.github.fschroffner.radiodroid3.data.mapper.toEntity
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.RadioStationDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

class HistoryManager @Inject constructor(
    @ApplicationContext ctx: Context,
    stationDao: RadioStationDao
) : StationSaveManager(ctx, stationDao) {

    override fun getSaveId() = "history"

    override val stationsFlow: Flow<List<DataRadioStation>> =
        stationDao.getHistoryStations().map { list -> list.map { it.toDataRadioStation() } }

    override fun add(station: DataRadioStation) {
        station.lastPlayed = System.currentTimeMillis()
        if (stationDao != null) {
            coroutineScope.launch {
                stationDao.insertOrUpdate(station.toEntity())
            }
        } else {
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
