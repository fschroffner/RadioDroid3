package com.github.fschroffner.radiodroid3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.fschroffner.radiodroid3.di.AppEntryPoint
import dagger.hilt.EntryPoints

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmManager = EntryPoints.get(context.applicationContext, AppEntryPoint::class.java).radioAlarmManager()
        alarmManager.resetAllAlarms()
    }
}
