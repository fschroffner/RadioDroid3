package com.github.fschroffner.radiodroid3.service

import android.app.PendingIntent
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.os.BundleCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.preference.PreferenceManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.BuildConfig
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.RadioDroidApp
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.history.TrackHistoryRepository
import com.github.fschroffner.radiodroid3.players.PlayState
import com.github.fschroffner.radiodroid3.players.RadioPlayer
import com.github.fschroffner.radiodroid3.players.selector.PlayerType
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.live.ShoutcastInfo
import com.github.fschroffner.radiodroid3.station.live.StreamLiveInfo
import java.io.ByteArrayOutputStream

class PlayerService : MediaLibraryService(), RadioPlayer.PlayerListener {

    companion object {
        const val METERED_CONNECTION_WARNING_KEY = "warn_no_wifi"
        const val PLAYER_SERVICE_TIMER_UPDATE = "com.github.fschroffner.radiodroid3.timerupdate"
        const val PLAYER_SERVICE_META_UPDATE = "com.github.fschroffner.radiodroid3.metaupdate"
        const val PLAYER_SERVICE_STATE_CHANGE = "com.github.fschroffner.radiodroid3.statechange"
        const val PLAYER_SERVICE_STATE_EXTRA_KEY = "state"
        const val PLAYER_SERVICE_METERED_CONNECTION = "com.github.fschroffner.radiodroid3.metered_connection"
        const val PLAYER_SERVICE_METERED_CONNECTION_PLAYER_TYPE = "PLAYER_TYPE"
        const val PLAYER_SERVICE_BOUND = "com.github.fschroffner.radiodroid3.playerservicebound"

        // Custom Media3 session commands backing the notification's radio-specific buttons.
        // Station switching is not a timeline seek, so it is exposed as custom commands instead of
        // the standard COMMAND_SEEK_TO_NEXT/PREVIOUS. These are also the transport commands the
        // in-app MediaController (PlayerServiceUtil) uses instead of the removed AIDL binder.
        const val CUSTOM_COMMAND_PREVIOUS = "com.github.fschroffner.radiodroid3.PREVIOUS"
        const val CUSTOM_COMMAND_NEXT = "com.github.fschroffner.radiodroid3.NEXT"
        const val CUSTOM_COMMAND_STOP = "com.github.fschroffner.radiodroid3.STOP"

        // Remaining radio-specific commands exposed to the in-app MediaController. They carry no
        // equivalent in Media3's standard transport controls (station selection, pause with a
        // reason, sleep timer, recording, metered-connection warning).
        //
        // MPD and Cast playback are intentionally NOT routed through here: they are handled outside
        // the service by PlayStationTask.playMPD / playCAST (invoked from the player selector),
        // which talk directly to MPDClient and CastHandler.
        const val CUSTOM_COMMAND_SET_STATION = "com.github.fschroffner.radiodroid3.SET_STATION"
        const val CUSTOM_COMMAND_PLAY_STATION = "com.github.fschroffner.radiodroid3.PLAY_STATION"
        const val CUSTOM_COMMAND_PAUSE = "com.github.fschroffner.radiodroid3.PAUSE"
        const val CUSTOM_COMMAND_RESUME = "com.github.fschroffner.radiodroid3.RESUME"
        const val CUSTOM_COMMAND_ADD_TIMER = "com.github.fschroffner.radiodroid3.ADD_TIMER"
        const val CUSTOM_COMMAND_CLEAR_TIMER = "com.github.fschroffner.radiodroid3.CLEAR_TIMER"
        const val CUSTOM_COMMAND_START_RECORDING = "com.github.fschroffner.radiodroid3.START_RECORDING"
        const val CUSTOM_COMMAND_STOP_RECORDING = "com.github.fschroffner.radiodroid3.STOP_RECORDING"
        const val CUSTOM_COMMAND_WARN_METERED = "com.github.fschroffner.radiodroid3.WARN_METERED"

        // Argument keys for the custom commands above.
        const val CMD_ARG_STATION = "station"
        const val CMD_ARG_IS_ALARM = "is_alarm"
        const val CMD_ARG_PAUSE_REASON = "pause_reason"
        const val CMD_ARG_TIMER_SECONDS = "timer_seconds"
        const val CMD_ARG_PLAYER_TYPE = "player_type"

        // Keys for the radio-specific state published through the Media3 session extras. Media3's
        // standard player state cannot express these, so they are mirrored into sessionExtras and
        // read back synchronously by PlayerServiceUtil.
        const val STATE_IS_PLAYING = "state_is_playing"
        const val STATE_PLAYER_STATE = "state_player_state"
        const val STATE_TIMER_SECONDS = "state_timer_seconds"
        const val STATE_METADATA_LIVE = "state_metadata_live"
        const val STATE_CURRENT_STATION = "state_current_station"
        const val STATE_STATION_ID = "state_station_id"
        const val STATE_SHOUTCAST_INFO = "state_shoutcast_info"
        const val STATE_IS_HLS = "state_is_hls"
        const val STATE_IS_RECORDING = "state_is_recording"
        const val STATE_RECORD_FILE_NAME = "state_record_file_name"
        const val STATE_TRANSFERRED_BYTES = "state_transferred_bytes"
        const val STATE_BUFFERED_SECONDS = "state_buffered_seconds"
        const val STATE_LAST_PLAY_START_TIME = "state_last_play_start_time"
        const val STATE_PAUSE_REASON = "state_pause_reason"
        const val STATE_NOTIFICATION_ACTIVE = "state_notification_active"

        private const val FULL_VOLUME = 100f
        private const val DUCK_VOLUME = 40f
        private const val METERED_CONNECTION_WARNING_COOLDOWN = 20 * 1000
        private const val SESSION_STATE_UPDATE_INTERVAL = 1000L
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
    // Persistent Media3 library session that hosts the notification (via
    // DefaultMediaNotificationProvider), serves the browse tree to clients such as Android Auto, and
    // backs any external Media3 controllers. Its player is a RadioMediaPlayer facade that lives for
    // the whole service lifetime, independent of the ExoPlayer which is released on pause/stop.
    private lateinit var media3Session: MediaLibrarySession
    private lateinit var radioMediaPlayer: RadioMediaPlayer
    private lateinit var browseTree: RadioBrowseTree

    // Extracted service components (see Step 4 of the PlayerService refactor): each owns one of the
    // cross-cutting concerns that used to be inlined here.
    private lateinit var playbackLocks: PlaybackLocks
    private lateinit var audioWarning: AudioWarning
    private lateinit var trackHistoryUpdater: TrackHistoryUpdater
    private lateinit var recordingController: RecordingController

    private val becomingNoisyReceiver = BecomingNoisyReceiver()
    private val headsetConnectionReceiver = HeadsetConnectionReceiver()
    private val connectivityChecker = ConnectivityChecker()

    private var pauseReason = PauseReason.NONE
    private var lastErrorFromPlayer = -1
    private var lastMeteredConnectionWarningTime = 0L
    private var liveInfo: StreamLiveInfo = StreamLiveInfo(null)
    private var streamInfo: ShoutcastInfo? = null
    private var isHls = false
    private var lastPlayStartTime = 0L
    private var notificationIsActive = false
    private var becomingNoisyRegistered = false

    val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    fun sendBroadCast(action: String) {
        val local = Intent().apply { setAction(action) }
        LocalBroadcastManager.getInstance(itsContext).sendBroadcast(local)
    }

    // Routes commands coming from the Media3 session / notification (play/pause/stop) back into
    // the existing radio playback logic.
    private val radioMediaPlayerCallback = object : RadioMediaPlayer.Callback {
        override fun onPlay() { resume() }
        override fun onPause() { pause(PauseReason.USER) }
        override fun onStop() { stop() }
    }

    // Grants the radio-specific custom commands, serves the browse tree, and handles notification
    // button presses as well as the in-app MediaController (PlayerServiceUtil) commands that
    // replaced the AIDL binder.
    private val media3SessionCallback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(SessionCommand(CUSTOM_COMMAND_PREVIOUS, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_NEXT, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_STOP, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_SET_STATION, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_PLAY_STATION, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_PAUSE, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_RESUME, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_ADD_TIMER, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_CLEAR_TIMER, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_START_RECORDING, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_STOP_RECORDING, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_COMMAND_WARN_METERED, Bundle.EMPTY))
                    .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        // A browsing controller (e.g. Android Auto) plays a station by "setting" its media item.
        // The item only carries the browse media id, so resolve it to a stored station and route it
        // into the radio playback path. Nothing is added to the facade's timeline (the radio engine
        // drives now-playing state), so an empty list is returned.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val station = mediaItems.asSequence()
                .mapNotNull { browseTree.stationForMediaId(it.mediaId) }
                .firstOrNull()
            if (station != null) {
                setStation(station)
                playCurrentStation(false)
            }
            return Futures.immediateFuture(mutableListOf<MediaItem>())
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootParams = LibraryParams.Builder().setExtras(browseTree.rootExtras()).build()
            return Futures.immediateFuture(LibraryResult.ofItem(browseTree.rootItem(), rootParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            Futures.immediateFuture(LibraryResult.ofItemList(browseTree.childrenOf(parentId), params))

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = browseTree.itemForMediaId(mediaId)
            return if (item != null) Futures.immediateFuture(LibraryResult.ofItem(item, null))
            else Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            args.classLoader = this@PlayerService.classLoader
            when (customCommand.customAction) {
                CUSTOM_COMMAND_PREVIOUS -> previous()
                CUSTOM_COMMAND_NEXT -> next()
                CUSTOM_COMMAND_STOP -> stop()
                CUSTOM_COMMAND_SET_STATION ->
                    BundleCompat.getParcelable(args, CMD_ARG_STATION, DataRadioStation::class.java)
                        ?.let { setStation(it) }
                CUSTOM_COMMAND_PLAY_STATION -> {
                    val station = BundleCompat.getParcelable(args, CMD_ARG_STATION, DataRadioStation::class.java)
                    if (station != null) setStation(station)
                    playCurrentStation(args.getBoolean(CMD_ARG_IS_ALARM, false))
                }
                CUSTOM_COMMAND_PAUSE -> {
                    val reason = BundleCompat.getParcelable(args, CMD_ARG_PAUSE_REASON, PauseReason::class.java)
                        ?: PauseReason.USER
                    pause(reason)
                }
                CUSTOM_COMMAND_RESUME -> resume()
                CUSTOM_COMMAND_ADD_TIMER -> addTimer(args.getInt(CMD_ARG_TIMER_SECONDS, 0))
                CUSTOM_COMMAND_CLEAR_TIMER -> clearTimer()
                CUSTOM_COMMAND_START_RECORDING -> startRecording()
                CUSTOM_COMMAND_STOP_RECORDING -> stopRecording()
                CUSTOM_COMMAND_WARN_METERED ->
                    BundleCompat.getParcelable(args, CMD_ARG_PLAYER_TYPE, PlayerType::class.java)
                        ?.let { warnAboutMeteredConnection(it) }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (!radioPlayer.isLocal()) return@OnAudioFocusChangeListener

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "audio focus gain")
                if (pauseReason == PauseReason.FOCUS_LOSS_TRANSIENT) {
                    registerBecomingNoisy()
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

    // Explicit type: the callback references `sleepTimer` inside its own initializer, so without an
    // explicit declared type K2 hits a recursive type-inference problem resolving `sleepTimer.seconds`.
    private val sleepTimer: SleepTimer = SleepTimer(object : SleepTimer.Callback {
        override fun onTick() {
            if (BuildConfig.DEBUG) Log.d(TAG, "${sleepTimer.seconds}")
            sendBroadCast(PLAYER_SERVICE_TIMER_UPDATE)
            publishSessionState()
        }

        override fun onFinish() {
            stop()
        }
    })

    // The metered-connection warning is reflected onto the Media3 notification through
    // updateNotification(PlayState.Paused) in warnAboutMeteredConnection, so the tone's start/finish
    // no longer needs to drive any separate media-session playback state.
    private val audioWarningCallback = object : AudioWarning.Callback {
        override fun onWarningStarted() {}
        override fun onWarningFinished() {}
    }

    private fun getTimerSeconds(): Long = sleepTimer.seconds

    private fun clearTimer() {
        if (sleepTimer.clear()) {
            sendBroadCast(PLAYER_SERVICE_TIMER_UPDATE)
            publishSessionState()
        }
    }

    private fun addTimer(secondsAdd: Int) = sleepTimer.add(secondsAdd)

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        media3Session

    override fun onCreate() {
        super.onCreate()

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        handler = Handler(mainLooper)
        itsContext = this
        audioManager = itsContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        radioIcon = ResourcesCompat.getDrawable(resources, R.drawable.ic_launcher, null) as BitmapDrawable

        playbackLocks = PlaybackLocks(this)
        audioWarning = AudioWarning(handler, audioWarningCallback)

        radioPlayer = RadioPlayer(this)
        radioPlayer.setPlayerListener(this)
        recordingController = RecordingController(this, (application as RadioDroidApp).recordingsManager, radioPlayer)

        browseTree = RadioBrowseTree(application as RadioDroidApp)

        val startActivityIntent = Intent(itsContext.applicationContext, ActivityMain::class.java)
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            itsContext.applicationContext, 0, startActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag
        )

        // Persistent Media3 player facade + library session that drive the notification and the
        // browse tree.
        radioMediaPlayer = RadioMediaPlayer(mainLooper, radioMediaPlayerCallback)
        media3Session = MediaLibrarySession.Builder(this, radioMediaPlayer, media3SessionCallback)
            .setId("RadioDroidPlayerService")
            .setCustomLayout(buildCustomLayout())
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(R.drawable.ic_play_arrow_white_24dp)
        setMediaNotificationProvider(notificationProvider)

        val radioDroidApp = application as RadioDroidApp
        trackHistoryRepository = radioDroidApp.trackHistoryRepository
        trackHistoryUpdater = TrackHistoryUpdater(trackHistoryRepository)

        val headsetConnectionFilter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        registerReceiver(headsetConnectionReceiver, headsetConnectionFilter)

        // Seed the session extras so a MediaController reading them right after connecting gets a
        // valid snapshot instead of an empty bundle.
        publishSessionState()
    }

    private fun buildCustomLayout(): List<CommandButton> = listOf(
        CommandButton.Builder()
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_PREVIOUS, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_skip_previous_24dp)
            .setDisplayName(getString(R.string.action_skip_to_previous))
            .build(),
        CommandButton.Builder()
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_NEXT, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_skip_next_24dp)
            .setDisplayName(getString(R.string.action_skip_to_next))
            .build(),
        CommandButton.Builder()
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_STOP, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_stop_white_24dp)
            .setDisplayName(getString(R.string.action_stop))
            .build()
    )

    override fun onDestroy() {
        if (BuildConfig.DEBUG) Log.d(TAG, "PlayService should be destroyed.")
        stop()
        stopSessionStateUpdates()
        media3Session.release()
        radioMediaPlayer.release()
        radioPlayer.destroy()
        unregisterBecomingNoisy()
        unregisterReceiver(headsetConnectionReceiver)
        super.onDestroy()
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

        if (intent != null) {
            when (intent.action) {
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

        // Let MediaLibraryService process its own media-button/session intents. The foreground
        // notification is now managed by the service via DefaultMediaNotificationProvider, driven
        // by the RadioMediaPlayer facade state.
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
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
            registerBecomingNoisy()
            liveInfo = StreamLiveInfo(null)
            streamInfo = null
            playbackLocks.acquire()
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

        playbackLocks.release()

        if (pauseReason != PauseReason.FOCUS_LOSS_TRANSIENT) {
            releaseAudioFocus()
        }

        radioPlayer.pause()
    }

    fun next() {
        val station = currentStation ?: return
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
        unregisterBecomingNoisy()
        radioPlayer.stop()
        playbackLocks.release()
        clearTimer()
        // Move the Media3 facade to idle so the service leaves the foreground and removes the
        // notification.
        updateNotification(PlayState.Idle)
        stopMeteredConnectionListener()
    }

    private fun startRecording() {
        if (!::recordingController.isInitialized) return
        recordingController.start()
        sendBroadCast(PLAYER_SERVICE_META_UPDATE)
        publishSessionState()
    }

    private fun stopRecording() {
        if (!::recordingController.isInitialized) return
        recordingController.stop()
        sendBroadCast(PLAYER_SERVICE_META_UPDATE)
        publishSessionState()
    }

    private fun isRecording(): Boolean = ::recordingController.isInitialized && recordingController.isRecording()

    private fun currentRecordFileName(): String? =
        if (::recordingController.isInitialized) recordingController.currentFileName() else null

    /**
     * Listens for [AudioManager.ACTION_AUDIO_BECOMING_NOISY] (e.g. headphones unplugged) while
     * playback is active so the service can pause. This used to be tied to the legacy
     * MediaSessionCompat's active state; it is now registered/unregistered directly.
     */
    private fun registerBecomingNoisy() {
        if (!becomingNoisyRegistered) {
            if (BuildConfig.DEBUG) Log.d(TAG, "registering becoming-noisy receiver.")
            registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            becomingNoisyRegistered = true
        }
    }

    private fun unregisterBecomingNoisy() {
        if (becomingNoisyRegistered) {
            if (BuildConfig.DEBUG) Log.d(TAG, "unregistering becoming-noisy receiver.")
            unregisterReceiver(becomingNoisyReceiver)
            becomingNoisyRegistered = false
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

    private fun toastOnUi(messageId: Int) {
        handler.post {
            Toast.makeText(itsContext, itsContext.resources.getString(messageId), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateNotification() {
        updateNotification(radioPlayer.getPlayState())
    }

    /**
     * Reflects the current radio [playState] onto the Media3 [RadioMediaPlayer] facade. The facade
     * in turn drives the foreground notification rendered by [DefaultMediaNotificationProvider].
     */
    private fun updateNotification(playState: PlayState) {
        updateMedia3PlayerState(playState)
        publishSessionState()
    }

    /**
     * Builds the radio-specific state that Media3's standard player state cannot express and which
     * [PlayerServiceUtil] reads back synchronously from the session extras.
     */
    private fun buildSessionStateExtras(): Bundle {
        val station = currentStation
        val playerInitialized = ::radioPlayer.isInitialized
        return Bundle().apply {
            putBoolean(STATE_IS_PLAYING, playerInitialized && radioPlayer.isPlaying())
            putParcelable(STATE_PLAYER_STATE, if (playerInitialized) radioPlayer.getPlayState() else PlayState.Idle)
            putLong(STATE_TIMER_SECONDS, sleepTimer.seconds)
            putParcelable(STATE_METADATA_LIVE, liveInfo)
            putParcelable(STATE_CURRENT_STATION, station)
            putString(STATE_STATION_ID, station?.StationUuid)
            putParcelable(STATE_SHOUTCAST_INFO, streamInfo)
            putBoolean(STATE_IS_HLS, isHls)
            putBoolean(STATE_IS_RECORDING, isRecording())
            putString(STATE_RECORD_FILE_NAME, currentRecordFileName())
            putLong(STATE_TRANSFERRED_BYTES, if (playerInitialized) radioPlayer.getCurrentPlaybackTransferredBytes() else 0L)
            putLong(STATE_BUFFERED_SECONDS, if (playerInitialized) radioPlayer.getBufferedSeconds() else 0L)
            putLong(STATE_LAST_PLAY_START_TIME, lastPlayStartTime)
            putParcelable(STATE_PAUSE_REASON, pauseReason)
            putBoolean(STATE_NOTIFICATION_ACTIVE, notificationIsActive)
        }
    }

    private fun publishSessionState() {
        if (!::media3Session.isInitialized) return
        media3Session.setSessionExtras(buildSessionStateExtras())
    }

    /**
     * While the radio is playing, byte counters and buffered-seconds change continuously. The old
     * AIDL client polled these on demand; the MediaController client instead reads the mirrored
     * session extras, so the service refreshes them on a fixed cadence during playback.
     */
    private val sessionStateUpdater = object : Runnable {
        override fun run() {
            if (::radioPlayer.isInitialized && radioPlayer.isPlaying()) {
                publishSessionState()
                handler.postDelayed(this, SESSION_STATE_UPDATE_INTERVAL)
            }
        }
    }

    private fun startSessionStateUpdates() {
        handler.removeCallbacks(sessionStateUpdater)
        handler.post(sessionStateUpdater)
    }

    private fun stopSessionStateUpdates() {
        handler.removeCallbacks(sessionStateUpdater)
    }

    private fun updateMedia3PlayerState(playState: PlayState) {
        if (!::radioMediaPlayer.isInitialized) return

        val station = currentStation
        if (playState == PlayState.Idle || station == null) {
            notificationIsActive = false
            radioMediaPlayer.update(Player.STATE_IDLE, false, null, MediaMetadata.EMPTY)
            return
        }

        // A non-idle state means the MediaLibraryService keeps a foreground notification up. This
        // flag lets ActivityMain decide whether tearing down its UI should also stop the service.
        notificationIsActive = true

        val statusText = notificationStatusText(playState)
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(station.Name)
            .setStation(station.Name)
            .setArtist(statusText)
        bitmapToPng(radioIcon.bitmap)?.let {
            metadataBuilder.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        val metadata = metadataBuilder.build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(station.StationUuid)
            .setMediaMetadata(metadata)
            .build()

        val (state, playWhenReady) = when (playState) {
            PlayState.PrePlaying -> Player.STATE_BUFFERING to true
            PlayState.Playing -> Player.STATE_READY to true
            PlayState.Paused -> Player.STATE_READY to false
            PlayState.Idle -> Player.STATE_IDLE to false
        }

        radioMediaPlayer.update(state, playWhenReady, mediaItem, metadata)
    }

    /**
     * Text shown as the second line of the notification, mirroring the messages the old custom
     * notification used to display (live track, metered-connection warning or playback errors).
     */
    private fun notificationStatusText(playState: PlayState): String {
        val res = itsContext.resources
        val currentPlayerState = radioPlayer.getPlayState()

        if ((currentPlayerState == PlayState.Paused || currentPlayerState == PlayState.Idle)
                && pauseReason == PauseReason.METERED_CONNECTION) {
            return res.getString(R.string.notify_metered_connection)
        }

        if (lastErrorFromPlayer != -1) {
            try {
                return res.getString(lastErrorFromPlayer)
            } catch (ex: Resources.NotFoundException) {
                Log.e(TAG, "Unknown play error: $lastErrorFromPlayer", ex)
            }
        }

        return when (playState) {
            PlayState.PrePlaying -> res.getString(R.string.notify_pre_play)
            PlayState.Playing ->
                if (!TextUtils.isEmpty(liveInfo.title)) liveInfo.title else res.getString(R.string.notify_play)
            PlayState.Paused -> res.getString(R.string.notify_paused)
            PlayState.Idle -> ""
        }
    }

    private fun bitmapToPng(bitmap: Bitmap): ByteArray? = try {
        ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Could not encode notification artwork", e)
        null
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

        audioWarning.play()

        val broadcast = Intent().apply {
            action = PLAYER_SERVICE_METERED_CONNECTION
            putExtra(PLAYER_SERVICE_METERED_CONNECTION_PLAYER_TYPE, playerType as Parcelable)
        }
        LocalBroadcastManager.getInstance(itsContext).sendBroadcast(broadcast)

        updateNotification(PlayState.Paused)
    }

    private fun forceStopAudioWarning() {
        audioWarning.forceStop()
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
                    registerBecomingNoisy()
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
                        unregisterBecomingNoisy()
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
                startSessionStateUpdates()
            } else {
                stopMeteredConnectionListener()
                stopSessionStateUpdates()
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
        publishSessionState()
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

            currentStation?.let { trackHistoryUpdater.onTrackChanged(it, liveInfo) }
        }
    }
}
