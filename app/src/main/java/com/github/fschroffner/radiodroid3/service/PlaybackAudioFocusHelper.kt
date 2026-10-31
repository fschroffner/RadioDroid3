package com.github.fschroffner.radiodroid3.service

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.widget.Toast
import com.github.fschroffner.radiodroid3.BuildConfig
import com.github.fschroffner.radiodroid3.R

/**
 * Handles AudioFocus requests, abandonment, and focus changes (gain, duck, transient loss, loss).
 */
class PlaybackAudioFocusHelper(
    private val context: Context,
    private val playbackLocks: PlaybackLocks,
    private val listener: AudioFocusChangeListener
) {
    interface AudioFocusChangeListener {
        fun onAudioFocusGain(wasTransientLoss: Boolean)
        fun onAudioFocusLoss()
        fun onAudioFocusLossTransient()
        fun onAudioFocusDuck(canDuck: Boolean)
        fun isLocalPlayer(): Boolean
        fun isPlaying(): Boolean
        fun getPauseReason(): PauseReason
    }

    companion object {
        private const val TAG = "PlaybackAudioFocus"
        const val FULL_VOLUME = 100f
        const val DUCK_VOLUME = 40f
    }

    val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (!listener.isLocalPlayer()) return@OnAudioFocusChangeListener

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "audio focus gain")
                val wasTransient = listener.getPauseReason() == PauseReason.FOCUS_LOSS_TRANSIENT
                listener.onAudioFocusGain(wasTransient)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "audio focus loss")
                if (listener.isPlaying()) {
                    listener.onAudioFocusLoss()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "audio focus loss transient")
                if (listener.isPlaying()) {
                    listener.onAudioFocusLossTransient()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "audio focus loss transient can duck")
                listener.onAudioFocusDuck(true)
            }
        }
    }

    fun acquireAudioFocus(): Int {
        val result = playbackLocks.acquireAudioFocus()
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Toast.makeText(context, context.getString(R.string.error_grant_audiofocus), Toast.LENGTH_SHORT).show()
        }
        return result
    }

    fun releaseAudioFocus() {
        playbackLocks.releaseAudioFocus()
    }
}
