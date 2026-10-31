package com.github.fschroffner.radiodroid3.service

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.fschroffner.radiodroid3.HistoryManager
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.data.datastore.AppPreferencesDataStore
import com.github.fschroffner.radiodroid3.di.AppEntryPoint
import com.github.fschroffner.radiodroid3.player.MediaControllerRepository
import com.github.fschroffner.radiodroid3.players.selector.PlayerType
import dagger.hilt.EntryPoints

/**
 * Receiver handling headset plug/bluetooth connection state changes.
 * Supports injected dependencies to avoid Service-Locator lookups.
 */
class HeadsetConnectionReceiver(
    private val injectedMediaControllerRepository: MediaControllerRepository? = null,
    private val injectedPreferences: AppPreferencesDataStore? = null,
    private val injectedHistoryManager: HistoryManager? = null
) : BroadcastReceiver() {

    private var headsetConnected = false

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = injectedPreferences
            ?: EntryPoints.get(context.applicationContext, AppEntryPoint::class.java).appPreferencesDataStore()
        val resumeOnHeadset = prefs.getBoolean("resume_on_headset", false)
        if (!resumeOnHeadset) return

        var play = false

        if (intent.action == Intent.ACTION_HEADSET_PLUG) {
            val state = intent.getIntExtra("state", -1)
            play = state == 1
        }

        if (intent.action == BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED ||
            intent.action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
            val previousState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1)

            if (state == BluetoothProfile.STATE_CONNECTED && previousState != BluetoothProfile.STATE_CONNECTED) {
                play = !headsetConnected
                headsetConnected = true
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                headsetConnected = false
            } else {
                headsetConnected = state == BluetoothProfile.STATE_CONNECTED
            }
        }

        if (play) {
            val historyMgr = injectedHistoryManager
                ?: EntryPoints.get(context.applicationContext, AppEntryPoint::class.java).historyManager()
            val repo = injectedMediaControllerRepository
                ?: EntryPoints.get(context.applicationContext, AppEntryPoint::class.java).mediaControllerRepository()

            val lastStation = historyMgr.getFirst()
            if (lastStation != null && !repo.isPlaying()) {
                Utils.playAndWarnIfMetered(
                    context,
                    lastStation,
                    PlayerType.RADIODROID,
                    Runnable { Utils.play(context, lastStation, repo) },
                    repo
                )
            }
        }
    }
}
