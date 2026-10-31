package com.github.fschroffner.radiodroid3.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.github.fschroffner.radiodroid3.data.datastore.AppPreferencesDataStore
import com.github.fschroffner.radiodroid3.di.AppEntryPoint
import com.github.fschroffner.radiodroid3.player.MediaControllerRepository
import dagger.hilt.EntryPoints

/**
 * Receiver handling audio becoming noisy events (e.g. unplugging wired headphones).
 * Supports injected dependencies to avoid Service-Locator lookups.
 */
class BecomingNoisyReceiver(
    private val injectedMediaControllerRepository: MediaControllerRepository? = null,
    private val injectedPreferences: AppPreferencesDataStore? = null
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
            val repo = injectedMediaControllerRepository
                ?: EntryPoints.get(context.applicationContext, AppEntryPoint::class.java).mediaControllerRepository()
            val prefs = injectedPreferences
                ?: EntryPoints.get(context.applicationContext, AppEntryPoint::class.java).appPreferencesDataStore()

            if (repo.isPlaying() && prefs.getBoolean("pause_when_noisy", true)) {
                repo.pause(PauseReason.BECAME_NOISY)
            }
        }
    }
}
