package com.github.fschroffner.radiodroid3.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.github.fschroffner.radiodroid3.di.AppEntryPoint
import dagger.hilt.EntryPoints

class BecomingNoisyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
            val entryPoint = EntryPoints.get(context.applicationContext, AppEntryPoint::class.java)
            val repo = entryPoint.mediaControllerRepository()
            if (repo.isPlaying() && entryPoint.appPreferencesDataStore().getBoolean("pause_when_noisy", true)) {
                repo.pause(PauseReason.BECAME_NOISY)
            }
        }
    }
}
