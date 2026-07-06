package com.github.fschroffner.radiodroid3.service

import android.os.CountDownTimer

/**
 * Encapsulates the sleep-timer countdown that stops playback after a configurable delay. Extracted
 * from [PlayerService]; the service keeps owning what happens on each tick (broadcast + session
 * state refresh) and on expiry (stop playback) through the [Callback].
 */
class SleepTimer(private val callback: Callback) {

    interface Callback {
        /** Fired every second while the timer runs, with the remaining [SleepTimer.seconds] updated. */
        fun onTick()

        /** Fired once when the countdown reaches zero. */
        fun onFinish()
    }

    var seconds: Long = 0L
        private set

    private var timer: CountDownTimer? = null

    fun add(secondsAdd: Int) {
        timer?.cancel()
        timer = null
        seconds += secondsAdd

        timer = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                seconds = millisUntilFinished / 1000
                callback.onTick()
            }

            override fun onFinish() {
                callback.onFinish()
                timer = null
            }
        }.start()
    }

    /**
     * Cancels a running timer and resets the remaining time.
     *
     * @return true if a timer was active (and callers should refresh their published state), false
     *         if there was nothing to clear.
     */
    fun clear(): Boolean {
        if (timer != null) {
            timer!!.cancel()
            timer = null
            seconds = 0
            return true
        }
        return false
    }
}
