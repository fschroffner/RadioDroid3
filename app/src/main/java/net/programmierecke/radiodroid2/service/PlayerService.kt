package net.programmierecke.radiodroid2.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.audiofx.AudioEffect
import android.net.wifi.WifiManager
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Parcelable
import android.os.RemoteException
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import androidx.preference.PreferenceManager
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import net.programmierecke.radiodroid2.ActivityMain
import net.programmierecke.radiodroid2.BuildConfig
import net.programmierecke.radiodroid2.FavouriteManager
import net.programmierecke.radiodroid2.HistoryManager
import net.programmierecke.radiodroid2.IPlayerService
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.RadioDroidApp
import net.programmierecke.radiodroid2.Utils
import net.programmierecke.radiodroid2.history.TrackHistoryEntry
import net.programmierecke.radiodroid2.history.TrackHistoryRepository
import net.programmierecke.radiodroid2.players.PlayState
import net.programmierecke.radiodroid2.players.RadioPlayer
import net.programmierecke.radiodroid2.players.selector.PlayerType
import net.programmierecke.radiodroid2.recording.RecordingsManager
import net.programmierecke.radiodroid2.recording.RunningRecordingInfo
import net.programmierecke.radiodroid2.station.DataRadioStation
import net.programmierecke.radiodroid2.station.live.ShoutcastInfo
import net.programmierecke.radiodroid2.station.live.StreamLiveInfo
import java.util.Calendar
import java.util.Date

class PlayerService : androidx.core.app.JobIntentService(), RadioPlayer.PlayerListener {

    companion object {
        protected const val NOTIFY_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "default"

        const val METERED_CONNECTION_WARNING_KEY = "warn_no_wifi"
        const val PLAYER_SERVICE_NO_NOTIFICATION_EXTRA = "no_notification"
        const val PLAYER_SERVICE_TIMER_UPDATE = "net.programmierecke.radiodroid2.timerupdate"
        const val PLAYER_SERVICE_META_UPDATE = "net.programmierecke.radiodroid2.metaupdate"
        const val PLAYER_SERVICE_STATE_CHANGE = "net.programmierecke.radiodroid2.statechange"
        const val PLAYER_SERVICE_STATE_EXTRA_KEY = "state"
        const val PLAYER_SERVICE_METERED_CONNECTION = "net.programmierecke.radiodroid2.metered_connection"
        const val PLAYER_SERVICE_METERED_CONNECTION_PLAYER_TYPE = "PLAYER_TYPE"
        const val PLAYER_SERVICE_BOUND = "net.programmierecke.radiodroid2.playerservicebound"

        private const val FULL_VOLUME = 100f
        private const val DUCK_VOLUME = 40f
        private const val METERED_CONNECTION_WARNING_COOLDOWN = 20 * 1000
        private const val AUDIO_WARNING_DURATION = 2000
    }

    private val TAG = "PLAY"
    private val ACTION_PAUSE = "pause"
    private val ACTION_RESUME = "resume"
    private val ACTION_SKIP_TO_NEXT = "next"
    private val ACTION_SKIP_TO_PREVIOUS = "previous"
    private val ACTION_STOP = "stop"

    private lateinit var sharedPref: SharedPreferences
    private lateinit var trackHistoryRepository: TrackHistoryRepository
    private lateinit var itsContext: Context
    private lateinit var handler: Handler

    private var currentStation: DataRadioStation? = null
    private lateinit var radioIcon: BitmapDrawable
    private lateinit var radioPlayer: RadioPlayer
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var powerManager: android.os.PowerManager
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val becomingNoisyReceiver = BecomingNoisyReceiver()
    private val headsetConnectionReceiver = HeadsetConnectionReceiver()
    private val connectivityChecker = ConnectivityChecker()

    private var pauseReason = PauseReason.NONE
    private var lastErrorFromPlayer = -1
    private var lastMeteredConnectionWarningTime = 0L
    private var toneGenerator: ToneGenerator? = null
    private var toneGeneratorStopRunnable: Runnable? = null
    private var timer: CountDownTimer? = null
    private var seconds = 0L
    private var liveInfo: StreamLiveInfo = StreamLiveInfo(null)
    private var streamInfo: ShoutcastInfo? = null
    private var isHls = false
    private var lastPlayStartTime = 0L
    private var notificationIsActive = false

    val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    fun sendBroadCast(action: String) {
        val local = Intent().apply { setAction(action) }
        LocalBroadcastManager.getInstance(itsContext).sendBroadcast(local)
    }

    private val itsBinder = object : IPlayerService.Stub() {
        override fun SetStation(station: DataRadioStation) {
            this@PlayerService.setStation(station)
        }

        @Throws(RemoteException::class)
        override fun SkipToNext() { this@PlayerService.next() }

        @Throws(RemoteException::class)
        override fun SkipToPrevious() { this@PlayerService.previous() }

        @Throws(RemoteException::class)
        override fun Play(isAlarm: Boolean) { this@PlayerService.playCurrentStation(isAlarm) }

        @Throws(RemoteException::class)
        override fun Pause(pauseReason: PauseReason) { this@PlayerService.pause(pauseReason) }

        @Throws(RemoteException::class)
        override fun Resume() { this@PlayerService.resume() }

        @Throws(RemoteException::class)
        override fun Stop() { this@PlayerService.stop() }

        @Throws(RemoteException::class)
        override fun addTimer(secondsAdd: Int) { this@PlayerService.addTimer(secondsAdd) }

        @Throws(RemoteException::class)
        override fun clearTimer() { this@PlayerService.clearTimer() }

        @Throws(RemoteException::class)
        override fun getTimerSeconds(): Long = this@PlayerService.getTimerSeconds()

        @Throws(RemoteException::class)
        override fun getCurrentStationID(): String? = currentStation?.StationUuid

        @Throws(RemoteException::class)
        override fun getCurrentStation(): DataRadioStation? = currentStation

        @Throws(RemoteException::class)
        override fun getMetadataLive(): StreamLiveInfo = this@PlayerService.liveInfo

        @Throws(RemoteException::class)
        override fun getShoutcastInfo(): ShoutcastInfo? = streamInfo

        @Throws(RemoteException::class)
        override fun getMediaSessionToken(): MediaSessionCompat.Token = this@PlayerService.mediaSession.sessionToken

        @Throws(RemoteException::class)
        override fun getIsHls(): Boolean = isHls

        @Throws(RemoteException::class)
        override fun isPlaying(): Boolean = radioPlayer.isPlaying()

        @Throws(RemoteException::class)
        override fun getPlayerState(): PlayState = radioPlayer.getPlayState()

        @Throws(RemoteException::class)
        override fun startRecording() {
            if (::radioPlayer.isInitialized) {
                val radioDroidApp = application as RadioDroidApp
                val recordingsManager: RecordingsManager = radioDroidApp.recordingsManager
                recordingsManager.record(this@PlayerService, radioPlayer)
                sendBroadCast(PLAYER_SERVICE_META_UPDATE)
            }
        }

        @Throws(RemoteException::class)
        override fun stopRecording() {
            if (::radioPlayer.isInitialized) {
                val radioDroidApp = application as RadioDroidApp
                val recordingsManager: RecordingsManager = radioDroidApp.recordingsManager
                recordingsManager.stopRecording(radioPlayer)
                sendBroadCast(PLAYER_SERVICE_META_UPDATE)
            }
        }

        @Throws(RemoteException::class)
        override fun isRecording(): Boolean = ::radioPlayer.isInitialized && radioPlayer.isRecording()

        @Throws(RemoteException::class)
        override fun getCurrentRecordFileName(): String? {
            if (::radioPlayer.isInitialized) {
                val radioDroidApp = application as RadioDroidApp
                val recordingsManager: RecordingsManager = radioDroidApp.recordingsManager
                val info: RunningRecordingInfo? = recordingsManager.getRecordingInfo(radioPlayer)
                if (info != null) return info.fileName
            }
            return null
        }

        @Throws(RemoteException::class)
        override fun getTransferredBytes(): Long =
            if (::radioPlayer.isInitialized) radioPlayer.getCurrentPlaybackTransferredBytes() else 0

        @Throws(RemoteException::class)
        override fun getBufferedSeconds(): Long =
            if (::radioPlayer.isInitialized) radioPlayer.getBufferedSeconds() else 0

        @Throws(RemoteException::class)
        override fun getLastPlayStartTime(): Long = lastPlayStartTime

        @Throws(RemoteException::class)
        override fun getPauseReason(): PauseReason = this@PlayerService.pauseReason

        @Throws(RemoteException::class)
        override fun enableMPD(hostname: String, port: Int) {}

        @Throws(RemoteException::class)
        override fun disableMPD() {}

        @Throws(RemoteException::class)
        override fun warnAboutMeteredConnection(playerType: PlayerType) {
            this@PlayerService.warnAboutMeteredConnection(playerType)
        }

        @Throws(RemoteException::class)
        override fun isNotificationActive(): Boolean = this@PlayerService.notificationIsActive
    }

    private var mediaSessionCallback: MediaSessionCompat.Callback? = null

    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (!radioPlayer.isLocal()) return@OnAudioFocusChangeListener

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "audio focus gain")
                if (pauseReason == PauseReason.FOCUS_LOSS_TRANSIENT) {
                    enableMediaSession()
                    resume()
                }
                radioPlayer.setVolume(FULL_VOLUME)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "audio focus loss")
                if (radioPlayer.isPlaying()) pause(PauseReason.FOCUS_LOSS)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "audio focus loss transient")
                if (radioPlayer.isPlaying()) pause(PauseReason.FOCUS_LOSS_TRANSIENT)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "audio focus loss transient can duck")
                radioPlayer.setVolume(DUCK_VOLUME)
            }
        }
    }

    private val connectivityCallback = object : ConnectivityChecker.ConnectivityCallback {
        override fun onConnectivityChanged(connected: Boolean, connectionType: ConnectivityChecker.ConnectionType) {
            if (connectionType == ConnectivityChecker.ConnectionType.METERED && sharedPref.getBoolean(METERED_CONNECTION_WARNING_KEY, false)) {
                warnAboutMeteredConnection(PlayerType.RADIODROID)
            }
        }
    }

    private fun getTimerSeconds(): Long = seconds

    private fun clearTimer() {
        if (timer != null) {
            timer!!.cancel()
            timer = null
            seconds = 0
            sendBroadCast(PLAYER_SERVICE_TIMER_UPDATE)
        }
    }

    private fun addTimer(secondsAdd: Int) {
        timer?.cancel()
        timer = null
        seconds += secondsAdd

        timer = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                seconds = millisUntilFinished / 1000
                if (BuildConfig.DEBUG) Log.d(TAG, "$seconds")
                sendBroadCast(PLAYER_SERVICE_TIMER_UPDATE)
            }
            override fun onFinish() {
                stop()
                timer = null
            }
        }.start()
    }

    override fun onBind(arg0: Intent): IBinder = itsBinder

    override fun onCreate() {
        super.onCreate()

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        handler = Handler(mainLooper)
        itsContext = this
        timer = null
        powerManager = itsContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        audioManager = itsContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        radioIcon = ResourcesCompat.getDrawable(resources, R.drawable.ic_launcher, null) as BitmapDrawable

        radioPlayer = RadioPlayer(this)
        radioPlayer.setPlayerListener(this)

        mediaSessionCallback = MediaSessionCallback(this, itsBinder)
        mediaSession = MediaSessionCompat(baseContext, baseContext.packageName)
        mediaSession.setCallback(mediaSessionCallback)

        val startActivityIntent = Intent(itsContext.applicationContext, ActivityMain::class.java)
        mediaSession.setSessionActivity(PendingIntent.getActivity(itsContext.applicationContext, 0, startActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag))

        setMediaPlaybackState(PlaybackStateCompat.STATE_NONE)

        val radioDroidApp = application as RadioDroidApp
        trackHistoryRepository = radioDroidApp.trackHistoryRepository

        val headsetConnectionFilter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        registerReceiver(headsetConnectionReceiver, headsetConnectionFilter)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "RadioDroid2 Player", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Channel description"
                enableLights(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) Log.d(TAG, "PlayService should be destroyed.")
        stop()
        mediaSession.release()
        radioPlayer.destroy()
        unregisterReceiver(headsetConnectionReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PlayerServiceUtil.bindService(itsContext.applicationContext)

        if (currentStation == null) {
            val radioDroidApp = application as RadioDroidApp
            currentStation = radioDroidApp.historyManager.getFirst()
        }
        if (currentStation == null) {
            val radioDroidApp = application as RadioDroidApp
            currentStation = radioDroidApp.favouriteManager.getFirst()
        }

        var showNotification = true

        if (intent != null) {
            val action = intent.action
            if (action != null) {
                when (action) {
                    ACTION_SKIP_TO_PREVIOUS -> previous()
                    ACTION_SKIP_TO_NEXT -> next()
                    ACTION_STOP -> { stop(); return START_NOT_STICKY }
                    ACTION_PAUSE -> pause(PauseReason.USER)
                    ACTION_RESUME -> resume()
                    Intent.ACTION_MEDIA_BUTTON -> {
                        @Suppress("DEPRECATION")
                        val key = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        if (key != null && key.action == KeyEvent.ACTION_UP) {
                            when (key.keyCode) {
                                KeyEvent.KEYCODE_MEDIA_PLAY -> resume()
                                KeyEvent.KEYCODE_MEDIA_NEXT -> next()
                                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> previous()
                            }
                        }
                    }
                }
            }

            MediaButtonReceiver.handleIntent(mediaSession, intent)
            showNotification = !intent.getBooleanExtra(PLAYER_SERVICE_NO_NOTIFICATION_EXTRA, false)
        }

        if (showNotification && !notificationIsActive) {
            if (currentStation == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Temporary", NotificationManager.IMPORTANCE_DEFAULT)
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
                    val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setContentTitle("").setContentText("").build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    } else {
                        startForeground(NOTIFY_ID, notification)
                    }
                    stopForeground(true)
                } else {
                    stopSelf()
                    return START_NOT_STICKY
                }
            } else {
                updateNotification(PlayState.Paused)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun playWithoutWarnings(station: DataRadioStation) {
        setStation(station)
        playCurrentStation(false)
    }

    private fun playAndWarnIfMetered(station: DataRadioStation) {
        val radioDroidApp = application as RadioDroidApp
        Utils.playAndWarnIfMetered(radioDroidApp, station, PlayerType.RADIODROID,
            Runnable { playWithoutWarnings(station) },
            object : Utils.MeteredWarningCallback {
                override fun warn(station1: DataRadioStation, playerType: PlayerType) {
                    setStation(station1)
                    warnAboutMeteredConnection(playerType)
                }
            })
    }

    fun setStation(station: DataRadioStation) {
        this.currentStation = station
    }

    fun playCurrentStation(isAlarm: Boolean) {
        if (Utils.shouldLoadIcons(itsContext)) downloadRadioIcon()

        val result = acquireAudioFocus()
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            enableMediaSession()
            liveInfo = StreamLiveInfo(null)
            streamInfo = null
            acquireWakeLockAndWifiLock()
            currentStation?.let { radioPlayer.play(it, isAlarm) }
        }
    }

    fun pause(pauseReason: PauseReason) {
        if (BuildConfig.DEBUG) Log.d(TAG, "pausing playback, reason ${pauseReason}")

        this.pauseReason = pauseReason
        forceStopAudioWarning()

        if (pauseReason == PauseReason.METERED_CONNECTION) {
            lastMeteredConnectionWarningTime = System.currentTimeMillis()
        }

        releaseWakeLockAndWifiLock()

        if (pauseReason != PauseReason.FOCUS_LOSS_TRANSIENT) {
            releaseAudioFocus()
        }

        radioPlayer.pause()
    }

    fun next() {
        val station = currentStation ?: return
        setMediaPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT)
        val nextStation = station.queue?.getNextById(station.StationUuid)
        if (nextStation != null) {
            if (radioPlayer.isPlaying()) playWithoutWarnings(nextStation)
            else playAndWarnIfMetered(nextStation)
        }
    }

    fun previous() {
        val station = currentStation ?: return
        val prevStation = station.queue?.getPreviousById(station.StationUuid)
        if (prevStation != null) {
            if (radioPlayer.isPlaying()) playWithoutWarnings(prevStation)
            else playAndWarnIfMetered(prevStation)
        }
    }

    fun resume() {
        if (BuildConfig.DEBUG) Log.d(TAG, "resuming playback.")

        forceStopAudioWarning()

        var bypassMeteredConnectionWarning = false

        if (pauseReason == PauseReason.METERED_CONNECTION) {
            val now = System.currentTimeMillis()
            val delta = now - lastMeteredConnectionWarningTime
            bypassMeteredConnectionWarning = delta < METERED_CONNECTION_WARNING_COOLDOWN && delta > 0
        }

        this.pauseReason = PauseReason.NONE
        this.lastMeteredConnectionWarningTime = 0

        if (!radioPlayer.isPlaying()) {
            val radioDroidApp = application as RadioDroidApp
            var station = currentStation
            if (currentStation == null) {
                station = radioDroidApp.historyManager.getFirst()
            }

            if (station != null) {
                if (bypassMeteredConnectionWarning) {
                    startMeteredConnectionListener()
                    acquireAudioFocus()
                    playWithoutWarnings(station)
                } else {
                    playAndWarnIfMetered(station)
                }
            }
        }
    }

    fun stop() {
        if (BuildConfig.DEBUG) Log.d(TAG, "stopping playback.")

        this.pauseReason = PauseReason.NONE
        this.lastMeteredConnectionWarningTime = 0
        this.notificationIsActive = false

        liveInfo = StreamLiveInfo(null)
        streamInfo = null

        forceStopAudioWarning()
        releaseAudioFocus()
        disableMediaSession()
        radioPlayer.stop()
        releaseWakeLockAndWifiLock()
        clearTimer()
        stopForeground(true)
        stopMeteredConnectionListener()
    }

    private fun setMediaPlaybackState(state: Int) {
        if (!::mediaSession.isInitialized) return

        var actions = (PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                or PlaybackStateCompat.ACTION_STOP
                or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                or PlaybackStateCompat.ACTION_PLAY_PAUSE)

        if (state == PlaybackStateCompat.STATE_BUFFERING || state == PlaybackStateCompat.STATE_PLAYING) {
            actions = actions or PlaybackStateCompat.ACTION_PAUSE
        } else {
            actions = actions or PlaybackStateCompat.ACTION_PLAY
        }

        val playbackStateBuilder = PlaybackStateCompat.Builder()
        playbackStateBuilder.setActions(actions)

        if (state == PlaybackStateCompat.STATE_ERROR) {
            var error = ""
            val currentPlayerState = radioPlayer.getPlayState()
            if ((currentPlayerState == PlayState.Paused || currentPlayerState == PlayState.Idle)
                    && pauseReason == PauseReason.METERED_CONNECTION) {
                error = itsContext.resources.getString(R.string.notify_metered_connection)
            } else {
                try {
                    error = itsContext.resources.getString(lastErrorFromPlayer)
                } catch (ex: Resources.NotFoundException) {
                    Log.e(TAG, "Unknown play error: $lastErrorFromPlayer", ex)
                }
            }
            playbackStateBuilder.setErrorMessage(PlaybackStateCompat.ERROR_CODE_ACTION_ABORTED, error)
        }

        playbackStateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
        mediaSession.setPlaybackState(playbackStateBuilder.build())
    }

    private fun enableMediaSession() {
        if (!mediaSession.isActive) {
            if (BuildConfig.DEBUG) Log.d(TAG, "enabling media session.")
            val becomingNoisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            registerReceiver(becomingNoisyReceiver, becomingNoisyFilter)
            mediaSession.isActive = true
            setMediaPlaybackState(PlaybackStateCompat.STATE_NONE)
            setMediaPlaybackState(PlaybackStateCompat.STATE_NONE)
        }
    }

    private fun disableMediaSession() {
        if (mediaSession.isActive) {
            if (BuildConfig.DEBUG) Log.d(TAG, "disabling media session.")
            mediaSession.isActive = false
            unregisterReceiver(becomingNoisyReceiver)
        }
    }

    private fun acquireAudioFocus(): Int {
        if (BuildConfig.DEBUG) Log.d(TAG, "acquiring audio focus.")
        @Suppress("DEPRECATION")
        val result = audioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.e(TAG, "acquiring audio focus failed!")
            toastOnUi(R.string.error_grant_audiofocus)
        }
        return result
    }

    private fun releaseAudioFocus() {
        if (BuildConfig.DEBUG) Log.d(TAG, "releasing audio focus.")
        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(afChangeListener)
    }

    fun acquireWakeLockAndWifiLock() {
        if (BuildConfig.DEBUG) Log.d(TAG, "acquiring wake lock and wifi lock.")

        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "PlayerService:")
        }
        if (!wakeLock!!.isHeld) {
            wakeLock!!.acquire()
        } else {
            if (BuildConfig.DEBUG) Log.d(TAG, "wake lock is already acquired.")
        }

        val wm = itsContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
        if (wm != null) {
            if (wifiLock == null) {
                wifiLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                    wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PlayerService")
                } else {
                    @Suppress("DEPRECATION")
                    wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "PlayerService")
                }
            }
            if (!wifiLock!!.isHeld) {
                wifiLock!!.acquire()
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "wifi lock is already acquired.")
            }
        } else {
            Log.e(TAG, "could not acquire wifi lock, WifiManager does not exist!")
        }
    }

    private fun releaseWakeLockAndWifiLock() {
        if (BuildConfig.DEBUG) Log.d(TAG, "releasing wake lock and wifi lock.")

        if (wakeLock != null) {
            if (wakeLock!!.isHeld) wakeLock!!.release()
            wakeLock = null
        }
        if (wifiLock != null) {
            if (wifiLock!!.isHeld) wifiLock!!.release()
            wifiLock = null
        }
    }

    private fun sendMessage(theTitle: String, theMessage: String, theTicker: String) {
        val notificationIntent = Intent(itsContext, ActivityMain::class.java).apply {
            putExtra("stationid", currentStation!!.StationUuid)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val stopIntent = Intent(itsContext, PlayerService::class.java).apply { action = ACTION_STOP }
        val pendingIntentStop = PendingIntent.getService(itsContext, 0, stopIntent, pendingIntentFlag)

        val nextIntent = Intent(itsContext, PlayerService::class.java).apply { action = ACTION_SKIP_TO_NEXT }
        val pendingIntentNext = PendingIntent.getService(itsContext, 0, nextIntent, pendingIntentFlag)

        val previousIntent = Intent(itsContext, PlayerService::class.java).apply { action = ACTION_SKIP_TO_PREVIOUS }
        val pendingIntentPrevious = PendingIntent.getService(itsContext, 0, previousIntent, pendingIntentFlag)

        val currentPlayerState = radioPlayer.getPlayState()

        var message = theMessage
        if ((currentPlayerState == PlayState.Paused || currentPlayerState == PlayState.Idle)
                && pauseReason == PauseReason.METERED_CONNECTION) {
            message = itsContext.resources.getString(R.string.notify_metered_connection)
        } else if (lastErrorFromPlayer != -1) {
            try {
                message = itsContext.resources.getString(lastErrorFromPlayer)
            } catch (ex: Resources.NotFoundException) {
                Log.e(TAG, "Unknown play error: $lastErrorFromPlayer", ex)
            }
        }

        val contentIntent = PendingIntent.getActivity(itsContext, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag)
        val notificationBuilder = NotificationCompat.Builder(itsContext, NOTIFICATION_CHANNEL_ID)
            .setContentIntent(contentIntent)
            .setContentTitle(theTitle)
            .setContentText(message)
            .setWhen(System.currentTimeMillis())
            .setTicker(theTicker)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_play_arrow_white_24dp)
            .setLargeIcon(radioIcon.bitmap)
            .addAction(R.drawable.ic_stop_white_24dp, getString(R.string.action_stop), pendingIntentStop)
            .addAction(R.drawable.ic_skip_previous_24dp, getString(R.string.action_skip_to_previous), pendingIntentPrevious)

        if (currentPlayerState == PlayState.Playing || currentPlayerState == PlayState.PrePlaying) {
            val pauseIntent = Intent(itsContext, PlayerService::class.java).apply { action = ACTION_PAUSE }
            val pendingIntentPause = PendingIntent.getService(itsContext, 0, pauseIntent, pendingIntentFlag)
            notificationBuilder.addAction(R.drawable.ic_pause_white_24dp, getString(R.string.action_pause), pendingIntentPause)
            notificationBuilder.setUsesChronometer(true).setOngoing(true)
        } else if (currentPlayerState == PlayState.Paused || currentPlayerState == PlayState.Idle) {
            val resumeIntent = Intent(itsContext, PlayerService::class.java).apply { action = ACTION_RESUME }
            val pendingIntentResume = PendingIntent.getService(itsContext, 0, resumeIntent, pendingIntentFlag)
            notificationBuilder.addAction(R.drawable.ic_play_arrow_white_24dp, getString(R.string.action_resume), pendingIntentResume)
            notificationBuilder.setUsesChronometer(false).setDeleteIntent(pendingIntentStop).setOngoing(false)
        }

        notificationBuilder.addAction(R.drawable.ic_skip_next_24dp, getString(R.string.action_skip_to_next), pendingIntentNext)
            .setStyle(MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(1, 2, 3)
                .setCancelButtonIntent(pendingIntentStop)
                .setShowCancelButton(true))

        val notification: Notification = notificationBuilder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFY_ID, notification)
        }
        notificationIsActive = true

        if (currentPlayerState == PlayState.Paused || currentPlayerState == PlayState.Idle) {
            stopForeground(false)
        }
    }

    private fun toastOnUi(messageId: Int) {
        handler.post {
            Toast.makeText(itsContext, itsContext.resources.getString(messageId), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateNotification() {
        updateNotification(radioPlayer.getPlayState())
    }

    private fun updateNotification(playState: PlayState) {
        when (playState) {
            PlayState.Idle -> {
                NotificationManagerCompat.from(this).cancel(NOTIFY_ID)
                setMediaPlaybackState(PlaybackStateCompat.STATE_NONE)
            }
            PlayState.PrePlaying -> {
                sendMessage(currentStation!!.Name,
                    itsContext.resources.getString(R.string.notify_pre_play),
                    itsContext.resources.getString(R.string.notify_pre_play))
                setMediaPlaybackState(PlaybackStateCompat.STATE_BUFFERING)
            }
            PlayState.Playing -> {
                val title = liveInfo.title
                if (!TextUtils.isEmpty(title)) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "update message:$title")
                    sendMessage(currentStation!!.Name, title, title)
                } else {
                    sendMessage(currentStation!!.Name, itsContext.resources.getString(R.string.notify_play), currentStation!!.Name)
                }

                if (::mediaSession.isInitialized) {
                    val builder = MediaMetadataCompat.Builder()
                    builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1)
                    builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentStation!!.Name)
                    builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, liveInfo.artist)
                    builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, liveInfo.track)
                    if (liveInfo.hasArtistAndTrack()) {
                        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, liveInfo.artist)
                        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, liveInfo.track)
                    } else {
                        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, liveInfo.title)
                        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentStation!!.Name)
                    }
                    builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, radioIcon.bitmap)
                    builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, radioIcon.bitmap)
                    mediaSession.setMetadata(builder.build())
                }

                setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING)
            }
            PlayState.Paused -> {
                sendMessage(currentStation!!.Name,
                    itsContext.resources.getString(R.string.notify_paused),
                    currentStation!!.Name)
                if (lastErrorFromPlayer != -1) {
                    setMediaPlaybackState(PlaybackStateCompat.STATE_ERROR)
                } else {
                    setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED)
                }
            }
        }
    }

    private fun downloadRadioIcon() {
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 70f, resources.displayMetrics)

        if (!currentStation!!.hasIcon()) {
            radioIcon = ResourcesCompat.getDrawable(resources, R.drawable.ic_launcher, null) as BitmapDrawable
            updateNotification()
            return
        }

        Picasso.get()
            .load(currentStation!!.IconUrl)
            .resize(px.toInt(), 0)
            .into(object : Target {
                override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
                    val useCircularIcons = Utils.useCircularIcons(itsContext)
                    if (!useCircularIcons) {
                        radioIcon = BitmapDrawable(resources, bitmap)
                    } else {
                        val rb = RoundedBitmapDrawableFactory.create(resources, bitmap)
                        rb.isCircular = true
                        radioIcon = BitmapDrawable(resources, rb.bitmap)
                    }
                    updateNotification()
                }

                override fun onBitmapFailed(e: Exception, errorDrawable: Drawable?) {}
                override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
            })
    }

    private fun warnAboutMeteredConnection(playerType: PlayerType) {
        stopMeteredConnectionListener()
        pause(PauseReason.METERED_CONNECTION)

        handler.post {
            setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING)
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGenerator!!.startTone(ToneGenerator.TONE_SUP_RADIO_NOTAVAIL, AUDIO_WARNING_DURATION)
        }

        toneGeneratorStopRunnable = Runnable {
            toneGenerator?.stopTone()
            toneGenerator?.release()
            toneGenerator = null
            toneGeneratorStopRunnable = null
            setMediaPlaybackState(PlaybackStateCompat.STATE_ERROR)
        }

        handler.postDelayed(toneGeneratorStopRunnable!!, AUDIO_WARNING_DURATION.toLong())

        val broadcast = Intent().apply {
            action = PLAYER_SERVICE_METERED_CONNECTION
            putExtra(PLAYER_SERVICE_METERED_CONNECTION_PLAYER_TYPE, playerType as Parcelable)
        }
        LocalBroadcastManager.getInstance(itsContext).sendBroadcast(broadcast)

        updateNotification(PlayState.Paused)
    }

    private fun forceStopAudioWarning() {
        if (toneGenerator != null) {
            handler.removeCallbacks(toneGeneratorStopRunnable!!)
            toneGeneratorStopRunnable = null

            handler.post {
                toneGenerator?.stopTone()
                toneGenerator?.release()
                toneGenerator = null
            }
        }
    }

    private fun startMeteredConnectionListener() {
        if (sharedPref.getBoolean(METERED_CONNECTION_WARNING_KEY, false)) {
            connectivityChecker.startListening(this, connectivityCallback)
        }
    }

    private fun stopMeteredConnectionListener() {
        connectivityChecker.stopListening(this)
    }

    override fun onStateChanged(state: PlayState, audioSessionId: Int) {
        handler.post {
            lastErrorFromPlayer = -1

            when (state) {
                PlayState.Paused -> {}
                PlayState.Playing -> {
                    enableMediaSession()
                    if (BuildConfig.DEBUG) Log.d(TAG, "Open audio effect control session, session id=$audioSessionId")
                    lastPlayStartTime = System.currentTimeMillis()

                    val i = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    }
                    itsContext.sendBroadcast(i)
                }
                else -> {
                    if (state != PlayState.PrePlaying) {
                        disableMediaSession()
                    }

                    if (audioSessionId > 0) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Close audio effect control session, session id=$audioSessionId")
                        val i = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                        }
                        itsContext.sendBroadcast(i)
                    }

                    if (state == PlayState.Idle) {
                        stop()
                    }
                }
            }

            if (state != PlayState.Paused && state != PlayState.Idle) {
                startMeteredConnectionListener()
            } else {
                stopMeteredConnectionListener()
            }

            updateNotification(state)

            val intent = Intent().apply {
                action = PLAYER_SERVICE_STATE_CHANGE
                putExtra(PLAYER_SERVICE_STATE_EXTRA_KEY, state as Parcelable)
            }
            LocalBroadcastManager.getInstance(itsContext).sendBroadcast(intent)
        }
    }

    override fun onPlayerWarning(messageId: Int) {
        onPlayerError(messageId)
    }

    override fun onPlayerError(messageId: Int) {
        handler.post {
            lastErrorFromPlayer = messageId
            toastOnUi(messageId)
            updateNotification()
        }
    }

    override fun onBufferedTimeUpdate(bufferedMs: Long) {}

    override fun foundShoutcastStream(info: ShoutcastInfo, isHls: Boolean) {
        this.streamInfo = info
        this.isHls = isHls
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Metadata offset:${info.metadataOffset}")
            Log.d(TAG, "Bitrate:${info.bitrate}")
            Log.d(TAG, "Name:${info.audioName}")
            Log.d(TAG, "Hls:$isHls")
            Log.d(TAG, "Server:${info.serverName}")
            Log.d(TAG, "AudioInfo:${info.audioInfo}")
        }
        sendBroadCast(PLAYER_SERVICE_META_UPDATE)
    }

    override fun foundLiveStreamInfo(liveInfo: StreamLiveInfo) {
        val oldLiveInfo = this.liveInfo
        this.liveInfo = liveInfo

        if (BuildConfig.DEBUG) {
            val rawMetadata = liveInfo.rawMetadata ?: emptyMap()
            for (key in rawMetadata.keys) {
                Log.i(TAG, "INFO:$key=${rawMetadata[key]}")
            }
        }

        if (oldLiveInfo.title != liveInfo.title) {
            sendBroadCast(PLAYER_SERVICE_META_UPDATE)
            updateNotification()

            val currentTime = Calendar.getInstance().time

            trackHistoryRepository.getLastInsertedHistoryItem { trackHistoryEntry, dao ->
                if (trackHistoryEntry != null && trackHistoryEntry.title == liveInfo.title) {
                    trackHistoryEntry.endTime = Date(0)
                    dao.update(trackHistoryEntry)
                } else {
                    dao.setCurrentPlayingTrackEndTime(currentTime)

                    val newTrackHistoryEntry = TrackHistoryEntry().apply {
                        stationUuid = currentStation!!.StationUuid
                        artist = liveInfo.artist
                        title = liveInfo.title
                        track = liveInfo.track
                        stationIconUrl = currentStation!!.IconUrl
                        startTime = currentTime
                        endTime = Date(0)
                    }
                    trackHistoryRepository.insert(newTrackHistoryEntry)
                }
            }
        }
    }

    override fun onHandleWork(intent: Intent) {
        Log.d(TAG, "onHandleWork called with intent: $intent")
    }
}
