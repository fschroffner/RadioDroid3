package com.github.fschroffner.floradio

import android.content.Context
import com.github.fschroffner.floradio.station.DataRadioStation

class FallbackStationsManager(ctx: Context) : StationSaveManager(ctx) {
    override fun Load() {
        listStations.clear()
        val str = context.resources
                .openRawResource(R.raw.fallback_stations)
                .bufferedReader()
                .use { it.readText() }
        val arr = DataRadioStation.DecodeJson(str)
        listStations.addAll(arr)
    }
}
