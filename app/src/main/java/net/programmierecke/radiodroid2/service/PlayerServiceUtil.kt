package net.programmierecke.radiodroid2.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.util.TypedValue
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import net.programmierecke.radiodroid2.BuildConfig
import net.programmierecke.radiodroid2.IPlayerService
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.players.PlayState
import net.programmierecke.radiodroid2.players.selector.PlayerType
import net.programmierecke.radiodroid2.station.DataRadioStation
import net.programmierecke.radiodroid2.station.live.ShoutcastInfo
import net.programmierecke.radiodroid2.station.live.StreamLiveInfo

object PlayerServiceUtil {
    private var mainContext: Context? = null
    private var mBound = false
    private var serviceConnection: ServiceConnection? = null
    private var itsPlayerService: IPlayerService? = null

    @JvmStatic
    fun startService(context: Context) {
        if (mBound) return
        val anIntent = Intent(context, PlayerService::class.java).apply {
            putExtra(PlayerService.PLAYER_SERVICE_NO_NOTIFICATION_EXTRA, true)
        }
        mainContext = context
        serviceConnection = getServiceConnection()
        context.bindService(anIntent, serviceConnection!!, Context.BIND_AUTO_CREATE)
        mBound = true
    }

    @JvmStatic
    fun bindService(context: Context) {
        if (mBound) return
        mainContext = context
        serviceConnection = getServiceConnection()
        context.bindService(Intent(context, PlayerService::class.java), serviceConnection!!, Context.BIND_AUTO_CREATE)
        mBound = true
    }

    private fun unBind(context: Context) {
        try { context.unbindService(serviceConnection!!) } catch (_: Exception) {}
        serviceConnection = null
        mBound = false
    }

    @JvmStatic
    fun shutdownService() {
        val ctx = mainContext ?: return
        try {
            if (BuildConfig.DEBUG) Log.d("PlayerServiceUtil", "PlayerServiceUtil: shutdownService")
            unBind(ctx)
            ctx.stopService(Intent(ctx, PlayerService::class.java))
            itsPlayerService = null
            serviceConnection = null
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d("PlayerServiceUtil", "PlayerServiceUtil: shutdownService E001:${e.message}")
        }
    }

    private fun getServiceConnection() = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            if (BuildConfig.DEBUG) Log.d("PLAYER", "Service came online")
            itsPlayerService = IPlayerService.Stub.asInterface(binder)
            LocalBroadcastManager.getInstance(mainContext!!).sendBroadcast(Intent().apply { action = PlayerService.PLAYER_SERVICE_BOUND })
        }
        override fun onServiceDisconnected(className: ComponentName) {
            if (BuildConfig.DEBUG) Log.d("PLAYER", "Service offline")
            unBind(mainContext!!)
            itsPlayerService = null
        }
    }

    @JvmStatic fun isServiceBound() = itsPlayerService != null

    @JvmStatic fun isPlaying() = try { itsPlayerService?.isPlaying() ?: false } catch (_: RemoteException) { false }

    @JvmStatic fun getPlayerState() = try { itsPlayerService?.playerState ?: PlayState.Idle } catch (_: RemoteException) { PlayState.Idle }

    @JvmStatic fun stop() { try { itsPlayerService?.Stop() } catch (e: RemoteException) { Log.e("", "$e") } }

    @JvmStatic fun play(station: DataRadioStation) {
        try { itsPlayerService?.SetStation(station); itsPlayerService?.Play(false) } catch (e: RemoteException) { Log.e("", "$e") }
    }

    @JvmStatic fun setStation(station: DataRadioStation) { try { itsPlayerService?.SetStation(station) } catch (e: RemoteException) { Log.e("", "$e") } }

    @JvmStatic fun skipToNext() { try { itsPlayerService?.SkipToNext() } catch (e: RemoteException) { Log.e("", "$e") } }

    @JvmStatic fun skipToPrevious() { try { itsPlayerService?.SkipToPrevious() } catch (e: RemoteException) { Log.e("", "$e") } }

    @JvmStatic fun pause(pauseReason: PauseReason) { try { itsPlayerService?.Pause(pauseReason) } catch (e: RemoteException) { Log.e("", "$e") } }

    @JvmStatic fun resume() { try { itsPlayerService?.Resume() } catch (e: RemoteException) { Log.e("", "$e") } }

    @JvmStatic fun clearTimer() { try { itsPlayerService?.clearTimer() } catch (e: RemoteException) { Log.e("", "$e") } }

    @JvmStatic fun addTimer(secondsAdd: Int) { try { itsPlayerService?.addTimer(secondsAdd) } catch (e: RemoteException) { Log.e("", "$e") } }

    @JvmStatic fun getTimerSeconds() = try { itsPlayerService?.timerSeconds ?: 0L } catch (_: RemoteException) { 0L }

    @JvmStatic fun getMetadataLive(): StreamLiveInfo = try { itsPlayerService?.metadataLive ?: StreamLiveInfo(null) } catch (_: RemoteException) { StreamLiveInfo(null) }

    @JvmStatic fun getStationId(): String? = try { itsPlayerService?.currentStationID } catch (_: RemoteException) { null }

    @JvmStatic fun getCurrentStation(): DataRadioStation? = try { itsPlayerService?.currentStation } catch (_: RemoteException) { null }

    @JvmStatic fun getStationIcon(holder: ImageView, fromUrl: String?) {
        if (fromUrl.isNullOrBlank()) return
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 70f, mainContext!!.resources.displayMetrics)
        val placeholder = AppCompatResources.getDrawable(holder.context, R.drawable.ic_photo_24dp)
        Picasso.get().load(fromUrl).placeholder(placeholder!!).resize(px.toInt(), 0)
            .networkPolicy(NetworkPolicy.OFFLINE)
            .into(holder, object : Callback {
                override fun onSuccess() {}
                override fun onError(e: Exception) {
                    Picasso.get().load(fromUrl).placeholder(placeholder).resize(px.toInt(), 0)
                        .networkPolicy(NetworkPolicy.NO_CACHE).into(holder)
                }
            })
    }

    @JvmStatic fun getShoutcastInfo(): ShoutcastInfo? = try { itsPlayerService?.shoutcastInfo } catch (_: RemoteException) { null }

    @JvmStatic fun startRecording() { try { itsPlayerService?.startRecording() } catch (e: RemoteException) { Log.e("", "$e") } }

    @JvmStatic fun stopRecording() { try { itsPlayerService?.stopRecording() } catch (e: RemoteException) { Log.e("", "$e") } }

    @JvmStatic fun isRecording() = try { itsPlayerService?.isRecording ?: false } catch (_: RemoteException) { false }

    @JvmStatic fun getCurrentRecordFileName(): String? = try { itsPlayerService?.currentRecordFileName } catch (_: RemoteException) { null }

    @JvmStatic fun getIsHls() = try { itsPlayerService?.isHls ?: false } catch (_: RemoteException) { false }

    @JvmStatic fun getTransferredBytes() = try { itsPlayerService?.transferredBytes ?: 0L } catch (_: RemoteException) { 0L }

    @JvmStatic fun getBufferedSeconds() = try { itsPlayerService?.bufferedSeconds ?: 0L } catch (_: RemoteException) { 0L }

    @JvmStatic fun getLastPlayStartTime() = try { itsPlayerService?.lastPlayStartTime ?: 0L } catch (_: RemoteException) { 0L }

    @JvmStatic fun getPauseReason() = try { itsPlayerService?.pauseReason ?: PauseReason.NONE } catch (_: RemoteException) { PauseReason.NONE }

    @JvmStatic fun enableMPD(hostname: String, port: Int) { try { itsPlayerService?.enableMPD(hostname, port) } catch (e: RemoteException) { Log.e("", "$e") } }

    fun disableMPD() { try { itsPlayerService?.disableMPD() } catch (e: RemoteException) { Log.e("", "$e") } }

    @JvmStatic fun warnAboutMeteredConnection(playerType: PlayerType) { try { itsPlayerService?.warnAboutMeteredConnection(playerType) } catch (e: RemoteException) { Log.e("", "$e") } }

    @JvmStatic fun isNotificationActive() = try { itsPlayerService?.isNotificationActive ?: false } catch (_: RemoteException) { false }
}
