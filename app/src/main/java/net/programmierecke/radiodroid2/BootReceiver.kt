package net.programmierecke.radiodroid2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val radioDroidApp = context.applicationContext as RadioDroidApp
        radioDroidApp.alarmManager.resetAllAlarms()
    }
}
