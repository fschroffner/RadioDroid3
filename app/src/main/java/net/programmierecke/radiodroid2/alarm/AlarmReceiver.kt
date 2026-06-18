package net.programmierecke.radiodroid2.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.AsyncTask
import android.os.IBinder
import android.os.PowerManager
import android.os.RemoteException
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import net.programmierecke.radiodroid2.BuildConfig
import net.programmierecke.radiodroid2.IPlayerService
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.RadioDroidApp
import net.programmierecke.radiodroid2.Utils
import net.programmierecke.radiodroid2.service.ConnectivityChecker
import net.programmierecke.radiodroid2.service.PlayerService
import net.programmierecke.radiodroid2.station.DataRadioStation

class AlarmReceiver : BroadcastReceiver() {
    private var url: String? = null
    private var alarmId = -1
    private var station: DataRadioStation? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var timeout = 10
    private var itsPlayerService: IPlayerService? = null
    private var playContext: Context? = null

    private val svcConn = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Service came online")
            itsPlayerService = IPlayerService.Stub.asInterface(binder)
            try {
                station!!.playableUrl = url
                itsPlayerService!!.SetStation(station)
                itsPlayerService!!.Play(true)
                itsPlayerService!!.addTimer(timeout * 60)
                (playContext!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(BACKUP_NOTIFICATION_ID)
            } catch (e: RemoteException) { Log.e(TAG, "play error:$e") }
            releaseLocks()
        }
        override fun onServiceDisconnected(className: ComponentName) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Service offline")
            itsPlayerService = null
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (BuildConfig.DEBUG) Log.d(TAG, "received broadcast")
        acquireLocks(context)
        Toast.makeText(context, context.resources.getText(R.string.alert_alarm_working), Toast.LENGTH_SHORT).show()

        alarmId = intent.getIntExtra("id", -1)
        val app = context.applicationContext as RadioDroidApp
        val ram = app.alarmManager
        station = ram.getStation(alarmId)
        ram.resetAllAlarms()

        if (station != null && alarmId >= 0) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(app)
            val warnOnMetered = prefs.getBoolean("warn_no_wifi", false)
            if (warnOnMetered && ConnectivityChecker.getCurrentConnectionType(app) == ConnectivityChecker.ConnectionType.METERED) {
                playSystemAlarm(context)
            } else {
                play(context, station!!.StationUuid)
            }
        } else {
            Toast.makeText(context, context.resources.getText(R.string.alert_alarm_not_working), Toast.LENGTH_SHORT).show()
        }
    }

    private fun acquireLocks(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (wakeLock == null) wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlarmReceiver:")
        if (wakeLock?.isHeld == false) { if (BuildConfig.DEBUG) Log.d(TAG, "acquire wakelock"); wakeLock!!.acquire() }
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: run { Log.e(TAG, "could not acquire wifi lock"); return }
        if (wifiLock == null) wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AlarmReceiver")
        if (wifiLock?.isHeld == false) { if (BuildConfig.DEBUG) Log.d(TAG, "acquire wifilock"); wifiLock!!.acquire() }
    }

    private fun releaseLocks() {
        wakeLock?.let { it.release(); wakeLock = null; if (BuildConfig.DEBUG) Log.d(TAG, "release wakelock") }
        wifiLock?.let { it.release(); wifiLock = null; if (BuildConfig.DEBUG) Log.d(TAG, "release wifilock") }
    }

    @Suppress("DEPRECATION")
    private fun play(context: Context, stationId: String) {
        val app = context.applicationContext as RadioDroidApp
        val httpClient = app.httpClient

        @Suppress("ObjectLiteralToLambda")
        object : AsyncTask<Void, Void, String?>() {
            override fun doInBackground(vararg params: Void?): String? {
                repeat(20) {
                    val result = Utils.getRealStationLink(httpClient, context, stationId)
                    if (result != null) return result
                    try { Thread.sleep(500) } catch (e: InterruptedException) { Log.e(TAG, "Play() $e") }
                }
                return null
            }

            override fun onPostExecute(result: String?) {
                if (result != null) {
                    url = result
                    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                    val playExternal = prefs.getBoolean("alarm_external", false)
                    val packageName = prefs.getString("shareapp_package", null)
                    val activityName = prefs.getString("shareapp_activity", null)
                    timeout = prefs.getString("alarm_timeout", "10")?.toIntOrNull() ?: 10
                    try {
                        if (playExternal && packageName != null && activityName != null) {
                            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                setClassName(packageName, activityName)
                                setDataAndType(Uri.parse(url), "audio/*")
                            })
                            releaseLocks()
                        } else {
                            playContext = context
                            val anIntent = Intent(context, PlayerService::class.java)
                            context.applicationContext.bindService(anIntent, svcConn, Context.BIND_AUTO_CREATE)
                            context.applicationContext.startService(anIntent)
                        }
                    } catch (e: Exception) { Log.e(TAG, "Error starting alarm intent $e"); playSystemAlarm(context) }
                } else {
                    Log.e(TAG, "Could not connect to radio station")
                    Toast.makeText(context, context.resources.getText(R.string.error_station_load), Toast.LENGTH_SHORT).show()
                    playSystemAlarm(context)
                    releaseLocks()
                }
            }
        }.execute()
    }

    private fun playSystemAlarm(context: Context) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Starting system alarm")
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(BACKUP_NOTIFICATION_NAME, context.getString(R.string.alarm_backup), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.alarm_back_desc)
            setSound(soundUri, AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        nm.createNotificationChannel(channel)
        nm.notify(BACKUP_NOTIFICATION_ID, NotificationCompat.Builder(context, BACKUP_NOTIFICATION_NAME)
            .setSmallIcon(R.drawable.ic_access_alarms_black_24dp)
            .setContentTitle(context.getString(R.string.action_alarm))
            .setContentText(context.getString(R.string.alarm_fallback_info))
            .setDefaults(Notification.DEFAULT_SOUND)
            .setSound(soundUri)
            .setAutoCancel(true).build())
    }

    companion object {
        private const val TAG = "RECV"
        const val BACKUP_NOTIFICATION_ID = 2
        const val BACKUP_NOTIFICATION_NAME = "backup-alarm"
    }
}
