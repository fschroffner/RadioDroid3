package net.programmierecke.radiodroid2.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.support.v4.media.MediaBrowserCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.MediaBrowserServiceCompat
import net.programmierecke.radiodroid2.IPlayerService
import net.programmierecke.radiodroid2.RadioDroidApp
import net.programmierecke.radiodroid2.utils.GetRealLinkAndPlayTask

class RadioDroidBrowserService : MediaBrowserServiceCompat() {

    private lateinit var radioDroidBrowser: RadioDroidBrowser
    private lateinit var playerServiceConnection: ServiceConnection
    private var playerService: IPlayerService? = null
    private var playTask: GetRealLinkAndPlayTask? = null

    private val playStationFromIdReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (MediaSessionCallback.BROADCAST_PLAY_STATION_BY_ID == intent.action) {
                val stationId = intent.getStringExtra(MediaSessionCallback.EXTRA_STATION_ID) ?: return
                val station = radioDroidBrowser.getStationById(stationId) ?: return
                playTask?.cancel(false)
                playTask = GetRealLinkAndPlayTask(context, station, playerService).also { it.execute() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        radioDroidBrowser = RadioDroidBrowser(application as RadioDroidApp)

        val anIntent = Intent(this, PlayerService::class.java).apply {
            putExtra(PlayerService.PLAYER_SERVICE_NO_NOTIFICATION_EXTRA, true)
        }
        startService(anIntent)

        playerServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                playerService = IPlayerService.Stub.asInterface(iBinder)
                try {
                    this@RadioDroidBrowserService.sessionToken = playerService!!.mediaSessionToken
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
            }
            override fun onServiceDisconnected(componentName: ComponentName) {
                playerService = null
            }
        }

        bindService(anIntent, playerServiceConnection, Context.BIND_AUTO_CREATE)

        LocalBroadcastManager.getInstance(this).registerReceiver(
            playStationFromIdReceiver,
            IntentFilter().apply { addAction(MediaSessionCallback.BROADCAST_PLAY_STATION_BY_ID) }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(playerServiceConnection)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? =
        radioDroidBrowser.onGetRoot(clientPackageName, clientUid, rootHints)

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        radioDroidBrowser.onLoadChildren(parentId, result)
    }
}
