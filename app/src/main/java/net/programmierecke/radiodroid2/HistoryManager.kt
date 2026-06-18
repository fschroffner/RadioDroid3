package net.programmierecke.radiodroid2

import android.content.Context
import net.programmierecke.radiodroid2.station.DataRadioStation

class HistoryManager(ctx: Context) : StationSaveManager(ctx) {
    override fun getSaveId() = "history"

    override fun add(station: DataRadioStation) {
        val existing = getById(station.StationUuid)
        if (existing != null) {
            listStations.remove(existing)
            listStations.add(0, existing)
            Save()
            return
        }
        cutList(MAXSIZE - 1)
        super.addFront(station)
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
