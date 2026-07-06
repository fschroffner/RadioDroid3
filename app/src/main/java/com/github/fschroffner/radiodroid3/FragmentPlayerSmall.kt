package com.github.fschroffner.radiodroid3

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.github.fschroffner.radiodroid3.history.TrackHistoryRepository
import com.github.fschroffner.radiodroid3.players.mpd.MPDClient
import com.github.fschroffner.radiodroid3.service.PauseReason
import com.github.fschroffner.radiodroid3.service.PlayerService
import com.github.fschroffner.radiodroid3.service.PlayerServiceUtil
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.StationActions

class FragmentPlayerSmall : Fragment() {

    enum class Role { HEADER, PLAYER }

    interface Callback {
        fun onToggle()
    }

    private lateinit var trackHistoryRepository: TrackHistoryRepository
    private lateinit var mpdClient: MPDClient
    private lateinit var updateUIReceiver: BroadcastReceiver
    private var callback: Callback? = null
    private var role = Role.PLAYER

    private lateinit var textViewStationName: TextView
    private lateinit var textViewLiveInfo: TextView
    private lateinit var textViewLiveInfoBig: TextView
    private lateinit var imageViewIcon: ImageView
    private lateinit var buttonPlay: ImageButton
    private lateinit var buttonMore: ImageButton

    private var firstPlayAttempted = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.layout_player_small, container, false)

        val radioDroidApp = requireActivity().application as RadioDroidApp
        mpdClient = radioDroidApp.mpdClient
        trackHistoryRepository = radioDroidApp.trackHistoryRepository

        updateUIReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    PlayerService.PLAYER_SERVICE_STATE_CHANGE,
                    PlayerService.PLAYER_SERVICE_META_UPDATE -> fullUpdate()
                    PlayerService.PLAYER_SERVICE_BOUND -> tryPlayAtStart()
                }
            }
        }

        textViewStationName = view.findViewById(R.id.textViewStationName)
        textViewLiveInfo = view.findViewById(R.id.textViewLiveInfo)
        textViewLiveInfoBig = view.findViewById(R.id.textViewLiveInfoBig)
        imageViewIcon = view.findViewById(R.id.playerRadioImage)
        buttonPlay = view.findViewById(R.id.buttonPlay)
        buttonMore = view.findViewById(R.id.buttonMore)

        return view
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        requireActivity().application.registerActivityLifecycleCallbacks(LifecycleCallbacks())

        buttonPlay.setOnClickListener {
            if (PlayerServiceUtil.isPlaying()) {
                if (PlayerServiceUtil.isRecording()) PlayerServiceUtil.stopRecording()
                PlayerServiceUtil.pause(PauseReason.USER)
            } else {
                playLastFromHistory()
            }
        }

        buttonMore.setOnClickListener { view ->
            val station = Utils.getCurrentOrLastStation(requireContext()) ?: return@setOnClickListener
            val favouriteManager = (requireActivity().application as RadioDroidApp).favouriteManager
            showPlayerMenu(station, favouriteManager.has(station.StationUuid))
        }

        requireView().setOnClickListener { callback?.onToggle() }

        tryPlayAtStart()
        fullUpdate()
        setupStationIcon()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(PlayerService.PLAYER_SERVICE_STATE_CHANGE)
            addAction(PlayerService.PLAYER_SERVICE_META_UPDATE)
            addAction(PlayerService.PLAYER_SERVICE_BOUND)
        }
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(updateUIReceiver, filter)
        fullUpdate()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(updateUIReceiver)
    }

    fun setCallback(callback: Callback) { this.callback = callback }

    fun setRole(role: Role) {
        this.role = role
        fullUpdate()
    }

    private fun playLastFromHistory() {
        val app = requireActivity().application as RadioDroidApp
        var station = PlayerServiceUtil.getCurrentStation()
        if (station == null) station = app.historyManager.getFirst()
        if (station != null && !PlayerServiceUtil.isPlaying()) {
            Utils.showPlaySelection(app, station, requireActivity().supportFragmentManager)
        }
    }

    private fun tryPlayAtStart() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext().applicationContext)
        var play = false
        var autoOff = false
        if (!firstPlayAttempted && PlayerServiceUtil.isServiceBound()) {
            firstPlayAttempted = true
            if (!PlayerServiceUtil.isPlaying()) {
                play = sharedPreferences.getBoolean("auto_play_on_startup", false)
            }
        }
        if (play) {
            autoOff = sharedPreferences.getBoolean("auto_off_on_startup", false)
            if (autoOff) {
                val timeout = try {
                    sharedPreferences.getString("auto_off_timeout", "10")!!.toInt()
                } catch (e: Exception) { 10 }
                PlayerServiceUtil.addTimer(timeout * 60)
            }
            playLastFromHistory()
        }
    }

    private fun setupStationIcon() {
        val useCircularIcons = PreferenceManager.getDefaultSharedPreferences(requireContext().applicationContext).getBoolean("circular_icons", false)
        if (useCircularIcons) {
            @Suppress("DEPRECATION")
            imageViewIcon.setBackgroundColor(requireContext().resources.getColor(android.R.color.black))
        }
        requireView().findViewById<ImageView>(R.id.transparentCircle).visibility = if (useCircularIcons) View.VISIBLE else View.GONE
    }

    private fun fullUpdate() {
        if (PlayerServiceUtil.isPlaying()) {
            buttonPlay.setImageResource(R.drawable.ic_pause_circle)
            buttonPlay.contentDescription = resources.getString(R.string.detail_pause)
        } else {
            buttonPlay.setImageResource(R.drawable.ic_play_circle)
            buttonPlay.contentDescription = resources.getString(R.string.detail_play)
        }

        val station = Utils.getCurrentOrLastStation(requireContext())
        val stationName = station?.Name ?: ""
        textViewStationName.text = stationName

        val liveInfo = PlayerServiceUtil.getMetadataLive()
        val streamTitle = liveInfo.title
        if (!TextUtils.isEmpty(streamTitle)) {
            textViewLiveInfo.visibility = View.VISIBLE
            textViewLiveInfo.text = streamTitle
            textViewStationName.gravity = Gravity.BOTTOM
        } else {
            textViewLiveInfo.visibility = View.GONE
            textViewStationName.gravity = Gravity.CENTER_VERTICAL
        }

        textViewLiveInfoBig.text = stationName

        if (!Utils.shouldLoadIcons(context)) {
            imageViewIcon.visibility = View.GONE
        } else if (station != null && station.hasIcon()) {
            imageViewIcon.visibility = View.VISIBLE
            PlayerServiceUtil.getStationIcon(imageViewIcon, station.IconUrl)
        } else {
            imageViewIcon.visibility = View.VISIBLE
            imageViewIcon.setImageResource(R.drawable.ic_launcher)
        }

        when (role) {
            Role.PLAYER -> {
                buttonPlay.visibility = View.VISIBLE
                buttonMore.visibility = View.GONE
                textViewStationName.visibility = View.VISIBLE
                textViewLiveInfoBig.visibility = View.GONE
            }
            Role.HEADER -> {
                buttonPlay.visibility = View.GONE
                buttonMore.visibility = View.VISIBLE
                textViewLiveInfo.visibility = View.GONE
                textViewStationName.visibility = View.GONE
                textViewLiveInfoBig.visibility = View.VISIBLE
            }
        }
    }

    private fun showPlayerMenu(currentStation: DataRadioStation, stationIsInFavourites: Boolean) {
        val dropDownMenu = PopupMenu(context, buttonMore)
        dropDownMenu.menuInflater.inflate(R.menu.menu_player, dropDownMenu.menu)
        dropDownMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_homepage -> StationActions.showWebLinks(requireActivity(), currentStation)
                R.id.action_share -> StationActions.share(requireContext(), currentStation)
                R.id.action_set_alarm -> StationActions.setAsAlarm(requireActivity(), currentStation)
                R.id.action_delete_stream_history -> trackHistoryRepository.deleteHistory()
            }
            true
        }
        dropDownMenu.show()
    }

    inner class LifecycleCallbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {
            context ?: return
            tryPlayAtStart()
        }
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
}
