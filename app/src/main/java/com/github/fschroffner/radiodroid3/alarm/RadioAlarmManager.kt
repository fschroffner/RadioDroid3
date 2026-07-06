package com.github.fschroffner.radiodroid3.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.github.fschroffner.radiodroid3.BuildConfig
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import java.util.Calendar
import java.util.Collections
import java.util.Observable

class RadioAlarmManager(private val context: Context) {

    private val list = mutableListOf<DataRadioStationAlarm>()
    private val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private inner class AlarmsObservable : Observable() {
        override fun hasChanged() = true
    }

    val savedAlarmsObservable: Observable = AlarmsObservable()

    init {
        load()
    }

    fun add(station: DataRadioStation, hour: Int, minute: Int) {
        if (BuildConfig.DEBUG) Log.d("ALARM", "added station:${station.Name}")
        val alarm = DataRadioStationAlarm().apply {
            this.station = station
            this.hour = hour
            this.minute = minute
            this.weekDays = ArrayList()
            this.id = getFreeId()
        }
        list.add(alarm)
        save()
        setEnabled(alarm.id, true)
    }

    fun getList(): Array<DataRadioStationAlarm> = list.toTypedArray()

    private fun getFreeId(): Int {
        var i = 0
        while (!checkIdFree(i)) i++
        if (BuildConfig.DEBUG) Log.d("ALARM", "new free id:$i")
        return i
    }

    private fun checkIdFree(id: Int) = list.none { it.id == id }

    private fun save() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        val gson = Gson()
        val ids = list.joinToString(",") { alarm ->
            if (BuildConfig.DEBUG) Log.d("ALARM", "save item:${alarm.id}/${alarm.station?.Name}")
            editor.putString("alarm.${alarm.id}.station", alarm.station?.toJson()?.toString())
            editor.putInt("alarm.${alarm.id}.timeHour", alarm.hour)
            editor.putInt("alarm.${alarm.id}.timeMinutes", alarm.minute)
            editor.putBoolean("alarm.${alarm.id}.enabled", alarm.enabled)
            editor.putBoolean("alarm.${alarm.id}.repeating", alarm.repeating)
            editor.putString("alarm.${alarm.id}.weekDays", gson.toJson(alarm.weekDays))
            alarm.id.toString()
        }
        editor.putString("alarm.ids", ids)
        editor.apply()
        savedAlarmsObservable.notifyObservers()
    }

    fun load() {
        list.clear()
        if (BuildConfig.DEBUG) Log.d("ALARM", "load()")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val ids = prefs.getString("alarm.ids", "") ?: ""
        if (ids.isNotEmpty()) {
            val idsArr = ids.split(",")
            if (BuildConfig.DEBUG) Log.d("ALARM", "load() - ${idsArr.size}")
            val gson = Gson()
            for (id in idsArr) {
                try {
                    val alarm = DataRadioStationAlarm()
                    alarm.station = DataRadioStation.DecodeJsonSingle(prefs.getString("alarm.$id.station", null))
                    val weekDaysString = prefs.getString("alarm.$id.weekDays", "[]") ?: "[]"
                    alarm.weekDays = gson.fromJson(weekDaysString, object : TypeToken<MutableList<Int>>() {}.type)
                    alarm.hour = prefs.getInt("alarm.$id.timeHour", 0)
                    alarm.minute = prefs.getInt("alarm.$id.timeMinutes", 0)
                    alarm.enabled = prefs.getBoolean("alarm.$id.enabled", false)
                    alarm.repeating = prefs.getBoolean("alarm.$id.repeating", false)
                    alarm.id = id.toInt()
                    if (alarm.station != null) list.add(alarm)
                } catch (e: Exception) {
                    Log.e("ALARM", "could not decode:$id")
                }
            }
        } else {
            Log.w("ALARM", "empty load() string")
        }
    }

    fun setEnabled(alarmId: Int, enabled: Boolean) {
        val alarm = getById(alarmId) ?: return
        if (enabled != alarm.enabled) {
            alarm.enabled = enabled
            save()
            if (enabled) start(alarmId) else stop(alarmId)
        }
    }

    private fun getById(id: Int) = list.firstOrNull { it.id == id }

    private fun start(alarmId: Int) {
        val alarm = getById(alarmId) ?: return
        stop(alarmId)

        val intent = Intent(context, AlarmReceiver::class.java).putExtra("id", alarmId)
        val alarmIntent = PendingIntent.getBroadcast(context, alarmId, intent, PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag)
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
        }

        if (calendar.timeInMillis < System.currentTimeMillis() + 60) {
            if (BuildConfig.DEBUG) Log.d("ALARM", "moved ahead one day")
            calendar.timeInMillis += ONE_DAY_IN_MILLIS
        }

        if (alarm.repeating) {
            var currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            Collections.sort(alarm.weekDays)
            var limiter = 6
            while (!alarm.weekDays.contains(currentDayOfWeek - 1) && limiter > 0) {
                calendar.timeInMillis += ONE_DAY_IN_MILLIS
                currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                limiter--
            }
        }

        Log.d("ALARM", "started:$alarmId ${calendar.get(Calendar.DAY_OF_WEEK)} " +
                "${calendar.get(Calendar.DAY_OF_MONTH)}.${calendar.get(Calendar.MONTH)} " +
                "${calendar.get(Calendar.HOUR_OF_DAY)}:${calendar.get(Calendar.MINUTE)}")

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                // On Android 12+ (API 31) SCHEDULE_EXACT_ALARM can be revoked by the user;
                // fall back to an inexact alarm so the app does not crash.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmMgr.canScheduleExactAlarms()) {
                    if (BuildConfig.DEBUG) Log.d("ALARM", "no exact-alarm permission, falling back to set()")
                    alarmMgr.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, alarmIntent)
                } else {
                    if (BuildConfig.DEBUG) Log.d("ALARM", "START setAlarmClock")
                    alarmMgr.setAlarmClock(AlarmManager.AlarmClockInfo(calendar.timeInMillis, alarmIntent), alarmIntent)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
                if (BuildConfig.DEBUG) Log.d("ALARM", "START setExact")
                alarmMgr.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, alarmIntent)
            }
            else -> {
                if (BuildConfig.DEBUG) Log.d("ALARM", "START set")
                alarmMgr.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, alarmIntent)
            }
        }
    }

    private fun stop(alarmId: Int) {
        getById(alarmId) ?: return
        if (BuildConfig.DEBUG) Log.d("ALARM", "stopped:$alarmId")
        val intent = Intent(context, AlarmReceiver::class.java)
        val alarmIntent = PendingIntent.getBroadcast(context, alarmId, intent, pendingIntentFlag)
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmIntent)
    }

    fun changeTime(alarmId: Int, hourOfDay: Int, minute: Int) {
        val alarm = getById(alarmId) ?: return
        alarm.hour = hourOfDay
        alarm.minute = minute
        save()
        if (alarm.enabled) { stop(alarmId); start(alarmId) }
    }

    fun changeWeekDays(alarmId: Int, weekday: Int) {
        val alarm = getById(alarmId) ?: return
        val pos = alarm.weekDays.indexOf(weekday)
        if (pos == -1) alarm.weekDays.add(weekday) else alarm.weekDays.removeAt(pos)
        save()
        start(alarmId)
    }

    fun remove(id: Int) {
        val alarm = getById(id) ?: return
        stop(id)
        list.remove(alarm)
        save()
    }

    fun getStation(stationId: Int): DataRadioStation? = getById(stationId)?.station

    fun resetAllAlarms() {
        for (alarm in list) {
            if (alarm.enabled) {
                if (BuildConfig.DEBUG) Log.d("ALARM", "started alarm with id:${alarm.id}")
                start(alarm.id)
            }
        }
    }

    fun toggleRepeating(id: Int) {
        val alarm = getById(id) ?: return
        alarm.repeating = !alarm.repeating
        save()
        start(id)
    }

    companion object {
        private const val ONE_DAY_IN_MILLIS = 24 * 60 * 60 * 1000
    }
}
