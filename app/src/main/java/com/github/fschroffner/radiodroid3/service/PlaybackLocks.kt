package com.github.fschroffner.radiodroid3.service

import android.content.Context
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.github.fschroffner.radiodroid3.BuildConfig

/**
 * Owns the [PowerManager.WakeLock] and [WifiManager.WifiLock] that keep the CPU and Wi-Fi radio
 * alive while a stream is playing, as well as the audio focus requests. 
 * Extracted from [PlayerService] so the lock lifecycle lives in one place.
 */
class PlaybackLocks(private val context: Context, private val audioManager: AudioManager, private val afChangeListener: AudioManager.OnAudioFocusChangeListener) {

    private val TAG = "PlaybackLocks"

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    
    private var audioFocusRequest: AudioFocusRequestCompat? = null

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

    fun acquireAudioFocus(): Int {
        if (BuildConfig.DEBUG) Log.d(TAG, "acquiring audio focus.")
        
        val audioAttributes = AudioAttributesCompat.Builder()
            .setUsage(AudioAttributesCompat.USAGE_MEDIA)
            .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
            .build()
            
        val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(afChangeListener)
            .build()
            
        audioFocusRequest = request
        val result = AudioManagerCompat.requestAudioFocus(audioManager, request)
        
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.e(TAG, "acquiring audio focus failed!")
        }
        return result
    }

    fun releaseAudioFocus() {
        if (BuildConfig.DEBUG) Log.d(TAG, "releasing audio focus.")
        audioFocusRequest?.let { 
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, it)
        }
    }
}
