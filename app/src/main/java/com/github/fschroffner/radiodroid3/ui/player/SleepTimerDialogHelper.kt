package com.github.fschroffner.radiodroid3.ui.player

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.player.MediaControllerRepository

/**
 * Encapsulates the Sleep Timer dialog presentation and timer adjustment.
 */
object SleepTimerDialogHelper {

    fun show(
        context: Context,
        mediaControllerRepository: MediaControllerRepository,
        sharedPref: SharedPreferences
    ) {
        val seekDialog = AlertDialog.Builder(context)
        val seekView = View.inflate(context, R.layout.layout_timer_chooser, null)

        seekDialog.setTitle(R.string.sleep_timer_title)
        seekDialog.setView(seekView)

        val seekTextView = seekView.findViewById<TextView>(R.id.timerTextView)
        val seekBar = seekView.findViewById<SeekBar>(R.id.timerSeekBar)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                seekTextView.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        val currentTimerSeconds = mediaControllerRepository.playbackState.value.sleepTimerSeconds
        val currentTimer = when {
            currentTimerSeconds <= 0 -> sharedPref.getInt("sleep_timer_default_minutes", 10).toLong()
            currentTimerSeconds < 60 -> 1L
            else -> currentTimerSeconds / 60
        }
        seekBar.progress = currentTimer.toInt()

        seekDialog.setPositiveButton(R.string.sleep_timer_apply) { _, _ ->
            mediaControllerRepository.clearTimer()
            mediaControllerRepository.addTimer(seekBar.progress * 60)
            sharedPref.edit().putInt("sleep_timer_default_minutes", seekBar.progress).apply()
        }

        seekDialog.setNegativeButton(R.string.sleep_timer_clear) { _, _ ->
            mediaControllerRepository.clearTimer()
        }

        seekDialog.create().show()
    }
}
