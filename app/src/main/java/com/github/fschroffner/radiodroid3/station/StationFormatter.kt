package com.github.fschroffner.radiodroid3.station

import android.content.Context
import android.text.TextUtils
import com.github.fschroffner.radiodroid3.R

object StationFormatter {

    fun getShortDetails(station: DataRadioStation, context: Context): String {
        val list = mutableListOf<String>()
        if (station.DeletedOnServer) list.add(context.resources.getString(R.string.station_detail_deleted_on_server))
        if (!station.Working) list.add(context.resources.getString(R.string.station_detail_broken))
        if (station.Bitrate > 0) list.add(context.resources.getString(R.string.station_detail_bitrate, station.Bitrate))
        if (station.State.trim().isNotEmpty()) list.add(station.State)
        if (station.Language.trim().isNotEmpty()) list.add(station.Language)
        return TextUtils.join(", ", list)
    }

    fun getLongDetails(station: DataRadioStation, context: Context): String {
        val list = mutableListOf<String>()
        if (station.DeletedOnServer) list.add(context.resources.getString(R.string.station_detail_deleted_on_server))
        if (!station.Working) list.add(context.resources.getString(R.string.station_detail_broken))
        if (station.Bitrate > 0) list.add(context.resources.getString(R.string.station_detail_bitrate, station.Bitrate))
        if (!TextUtils.isEmpty(station.Codec)) list.add(station.Codec!!)
        if (station.State.trim().isNotEmpty()) list.add(station.State)
        if (station.Language.trim().isNotEmpty()) list.add(station.Language)
        return TextUtils.join(", ", list)
    }
}
