package net.programmierecke.radiodroid2.service

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import net.programmierecke.radiodroid2.BuildConfig

/**
 * Owns the [PowerManager.WakeLock] and [WifiManager.WifiLock] that keep the CPU and Wi-Fi radio
 * alive while a stream is playing. Extracted from [PlayerService] so the lock lifecycle lives in one
 * place instead of being interleaved with playback control.
 */
class PlaybackLocks(private val context: Context) {

    private val TAG = "PlaybackLocks"

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun acquire() {
        if (BuildConfig.DEBUG) Log.d(TAG, "acquiring wake lock and wifi lock.")

        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PlayerService:")
        }
        if (!wakeLock!!.isHeld) {
            wakeLock!!.acquire()
        } else {
            if (BuildConfig.DEBUG) Log.d(TAG, "wake lock is already acquired.")
        }

        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
        if (wm != null) {
            if (wifiLock == null) {
                wifiLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                    wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PlayerService")
                } else {
                    @Suppress("DEPRECATION")
                    wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "PlayerService")
                }
            }
            if (!wifiLock!!.isHeld) {
                wifiLock!!.acquire()
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "wifi lock is already acquired.")
            }
        } else {
            Log.e(TAG, "could not acquire wifi lock, WifiManager does not exist!")
        }
    }

    fun release() {
        if (BuildConfig.DEBUG) Log.d(TAG, "releasing wake lock and wifi lock.")

        if (wakeLock != null) {
            if (wakeLock!!.isHeld) wakeLock!!.release()
            wakeLock = null
        }
        if (wifiLock != null) {
            if (wifiLock!!.isHeld) wifiLock!!.release()
            wifiLock = null
        }
    }
}
