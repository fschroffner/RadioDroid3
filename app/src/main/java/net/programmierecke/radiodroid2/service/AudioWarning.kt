package net.programmierecke.radiodroid2.service

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler

/**
 * Plays the short warning tone used when playback is blocked on a metered connection. Extracted from
 * [PlayerService] so the [ToneGenerator] lifecycle (start, delayed release, forced stop) is
 * self-contained. The service reacts to the tone starting/finishing through the [Callback], keeping
 * the media-session bookkeeping on its side.
 */
class AudioWarning(
    private val handler: Handler,
    private val callback: Callback
) {

    interface Callback {
        /** Fired on the handler thread right before the warning tone starts. */
        fun onWarningStarted()

        /** Fired on the handler thread once the warning tone has finished playing. */
        fun onWarningFinished()
    }

    private var toneGenerator: ToneGenerator? = null
    private var stopRunnable: Runnable? = null

    fun play() {
        handler.post {
            callback.onWarningStarted()
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGenerator!!.startTone(ToneGenerator.TONE_SUP_RADIO_NOTAVAIL, WARNING_DURATION)
        }

        stopRunnable = Runnable {
            releaseTone()
            stopRunnable = null
            callback.onWarningFinished()
        }
        handler.postDelayed(stopRunnable!!, WARNING_DURATION.toLong())
    }

    fun forceStop() {
        if (toneGenerator != null) {
            stopRunnable?.let { handler.removeCallbacks(it) }
            stopRunnable = null
            handler.post { releaseTone() }
        }
    }

    private fun releaseTone() {
        toneGenerator?.stopTone()
        toneGenerator?.release()
        toneGenerator = null
    }

    companion object {
        const val WARNING_DURATION = 2000
    }
}
