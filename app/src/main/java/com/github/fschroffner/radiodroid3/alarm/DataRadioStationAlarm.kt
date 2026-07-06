package com.github.fschroffner.radiodroid3.alarm

import com.github.fschroffner.radiodroid3.station.DataRadioStation

class DataRadioStationAlarm {
    var station: DataRadioStation? = null
    var id: Int = 0
    var hour: Int = 0
    var minute: Int = 0
    var repeating: Boolean = false
    var weekDays: ArrayList<Int> = ArrayList()
    var enabled: Boolean = false
}
