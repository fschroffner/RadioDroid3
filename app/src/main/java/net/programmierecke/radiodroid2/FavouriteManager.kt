package net.programmierecke.radiodroid2

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import net.programmierecke.radiodroid2.station.DataRadioStation

class FavouriteManager(ctx: Context) : StationSaveManager(ctx) {
    override fun getSaveId() = "favourites"

    init {
        setStationStatusListener(object : StationStatusListener {
            override fun onStationStatusChanged(station: DataRadioStation, favourite: Boolean) {
                val local = Intent(DataRadioStation.RADIO_STATION_LOCAL_INFO_CHAGED).apply {
                    putExtra(DataRadioStation.RADIO_STATION_UUID, station.StationUuid)
                }
                LocalBroadcastManager.getInstance(ctx).sendBroadcast(local)
            }
        })
    }

    override fun add(station: DataRadioStation) {
        if (!has(station.StationUuid)) super.add(station)
    }

    override fun restore(station: DataRadioStation, pos: Int) {
        if (!has(station.StationUuid)) super.restore(station, pos)
    }

    override fun Load() {
        super.Load()
        updateShortcuts()
    }

    override fun Save() {
        super.Save()
        updateShortcuts()
    }

    fun updateShortcuts() {
        if (Build.VERSION.SDK_INT >= 25 && !BuildConfig.IS_TESTING.get()) {
            val number = minOf(listStations.size, ActivityMain.MAX_DYNAMIC_LAUNCHER_SHORTCUTS)
            val setter = SetDynamicAppLauncherShortcuts(number)
            for (i in 0 until number) {
                listStations[i].prepareShortcut(context, setter)
            }
        }
    }

    @TargetApi(25)
    inner class SetDynamicAppLauncherShortcuts(private val expectedNumber: Int) : DataRadioStation.ShortcutReadyListener {
        private val shortcuts = ArrayList<ShortcutInfo>(expectedNumber)

        override fun onShortcutReadyListener(shortcutInfo: ShortcutInfo) {
            shortcuts.add(shortcutInfo)
            if (shortcuts.size >= expectedNumber) {
                val shortcutManager = context.applicationContext.getSystemService(ShortcutManager::class.java)
                shortcutManager.removeAllDynamicShortcuts()
                shortcutManager.setDynamicShortcuts(shortcuts)
            }
        }
    }
}
