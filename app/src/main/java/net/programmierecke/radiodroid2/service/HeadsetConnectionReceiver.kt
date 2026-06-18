package net.programmierecke.radiodroid2.service

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.preference.PreferenceManager
import net.programmierecke.radiodroid2.RadioDroidApp
import net.programmierecke.radiodroid2.Utils
import net.programmierecke.radiodroid2.players.selector.PlayerType

class HeadsetConnectionReceiver : BroadcastReceiver() {
    private var headsetConnected: Boolean? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (PlayerServiceUtil.getPauseReason() != PauseReason.BECAME_NOISY) return

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val resumeOnWired = sharedPref.getBoolean("auto_resume_on_wired_headset_connection", false)
        val resumeOnBluetooth = sharedPref.getBoolean("auto_resume_on_bluetooth_a2dp_connection", false)

        if (!resumeOnWired && !resumeOnBluetooth) return
        if (PlayerServiceUtil.isPlaying()) return

        var play = false

        when (intent.action) {
            AudioManager.ACTION_HEADSET_PLUG -> if (resumeOnWired) {
                val state = intent.getIntExtra("state", 0)
                play = state == 1 && headsetConnected == false
                headsetConnected = state == 1
            }
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> if (resumeOnBluetooth) {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                play = state == BluetoothProfile.STATE_CONNECTED && headsetConnected == false
                headsetConnected = state == BluetoothProfile.STATE_CONNECTED
            }
        }

        if (play) {
            val app = context.applicationContext as RadioDroidApp
            val lastStation = app.historyManager.getFirst()
            if (lastStation != null && !PlayerServiceUtil.isPlaying() && !app.getMpdClient().isMpdEnabled) {
                Utils.playAndWarnIfMetered(app, lastStation, PlayerType.RADIODROID) { Utils.play(app, lastStation) }
            }
        }
    }
}
