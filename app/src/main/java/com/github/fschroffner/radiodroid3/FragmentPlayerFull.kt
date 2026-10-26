package com.github.fschroffner.radiodroid3

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.Group
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.PagedList
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.squareup.picasso.Picasso
import com.github.fschroffner.radiodroid3.history.TrackHistoryAdapter
import com.github.fschroffner.radiodroid3.history.TrackHistoryEntry
import com.github.fschroffner.radiodroid3.history.TrackHistoryViewModel
import com.github.fschroffner.radiodroid3.player.PlayerUiState
import com.github.fschroffner.radiodroid3.player.PlayerViewModel
import com.github.fschroffner.radiodroid3.recording.RecordingsAdapter
import com.github.fschroffner.radiodroid3.recording.RunningRecordingInfo
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.views.RecyclerAwareNestedScrollView
import com.github.fschroffner.radiodroid3.views.TagsView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FragmentPlayerFull : Fragment() {

    private val PERM_REQ_STORAGE_RECORD = 1001

    interface TouchInterceptListener {
        fun requestDisallowInterceptTouchEvent(disallow: Boolean)
    }

    private var touchInterceptListener: TouchInterceptListener? = null
    private val playerViewModel: PlayerViewModel by activityViewModels()

    private lateinit var trackHistoryAdapter: TrackHistoryAdapter
    private lateinit var recordingsAdapter: RecordingsAdapter

    private var storagePermissionsDenied = false

    private lateinit var scrollViewContent: RecyclerAwareNestedScrollView
    private lateinit var pagerArtAndInfo: ViewPager
    private lateinit var artAndInfoPagerAdapter: ArtAndInfoPagerAdapter
    private lateinit var textViewGeneralInfo: TextView
    private lateinit var textViewTimePlayed: TextView
    private lateinit var textViewNetworkUsageInfo: TextView
    private lateinit var textViewTimeCached: TextView
    private lateinit var groupRecordings: Group
    private lateinit var imgRecordingIcon: ImageView
    private lateinit var textViewRecordingSize: TextView
    private lateinit var textViewRecordingName: TextView
    private lateinit var pagerHistoryAndRecordings: ViewPager
    private lateinit var historyAndRecordsPagerAdapter: HistoryAndRecordsPagerAdapter
    private lateinit var trackHistoryViewModel: TrackHistoryViewModel
    private lateinit var btnPlay: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnRecord: ImageButton
    private lateinit var btnFavourite: ImageButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        trackHistoryAdapter = TrackHistoryAdapter(requireActivity())
        trackHistoryAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val lm = historyAndRecordsPagerAdapter.recyclerViewSongHistory.layoutManager as LinearLayoutManager
                if (lm.findFirstVisibleItemPosition() < 2) {
                    historyAndRecordsPagerAdapter.recyclerViewSongHistory.scrollToPosition(0)
                }
            }
        })

        val view = inflater.inflate(R.layout.layout_player_full, container, false)
        scrollViewContent = view.findViewById(R.id.scrollViewContent)

        pagerArtAndInfo = view.findViewById(R.id.pagerArtAndInfo)
        artAndInfoPagerAdapter = ArtAndInfoPagerAdapter(requireContext(), pagerArtAndInfo)
        pagerArtAndInfo.adapter = artAndInfoPagerAdapter

        pagerArtAndInfo.setOnTouchListener(object : View.OnTouchListener {
            private val DRAG_THRESHOLD = 30
            private var downX = 0
            private var downY = 0
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX.toInt()
                        downY = event.rawY.toInt()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = Math.abs(event.rawX.toInt() - downX)
                        val dy = Math.abs(event.rawY.toInt() - downY)
                        if (dx > dy && dx > DRAG_THRESHOLD) {
                            pagerArtAndInfo.parent.requestDisallowInterceptTouchEvent(true)
                            scrollViewContent.parent.requestDisallowInterceptTouchEvent(false)
                            touchInterceptListener?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        scrollViewContent.parent.requestDisallowInterceptTouchEvent(false)
                        pagerArtAndInfo.parent.requestDisallowInterceptTouchEvent(false)
                        touchInterceptListener?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                return false
            }
        })

        textViewGeneralInfo = view.findViewById(R.id.textViewGeneralInfo)
        textViewTimePlayed = view.findViewById(R.id.textViewTimePlayed)
        textViewNetworkUsageInfo = view.findViewById(R.id.textViewNetworkUsageInfo)
        textViewTimeCached = view.findViewById(R.id.textViewTimeCached)
        groupRecordings = view.findViewById(R.id.group_recording_info)
        imgRecordingIcon = view.findViewById(R.id.imgRecordingIcon)
        textViewRecordingSize = view.findViewById(R.id.textViewRecordingSize)
        textViewRecordingName = view.findViewById(R.id.textViewRecordingName)

        pagerHistoryAndRecordings = view.findViewById(R.id.pagerHistoryAndRecordings)
        historyAndRecordsPagerAdapter = HistoryAndRecordsPagerAdapter(requireContext(), pagerHistoryAndRecordings)
        pagerHistoryAndRecordings.adapter = historyAndRecordsPagerAdapter

        btnPlay = view.findViewById(R.id.buttonPlay)
        btnPrev = view.findViewById(R.id.buttonPrev)
        btnNext = view.findViewById(R.id.buttonNext)
        btnRecord = view.findViewById(R.id.buttonRecord)
        btnFavourite = view.findViewById(R.id.buttonFavorite)

        historyAndRecordsPagerAdapter.recyclerViewSongHistory.adapter = trackHistoryAdapter
        val llmHistory = LinearLayoutManager(context).apply { orientation = RecyclerView.VERTICAL }
        historyAndRecordsPagerAdapter.recyclerViewSongHistory.layoutManager = llmHistory
        val divider = DividerItemDecoration(historyAndRecordsPagerAdapter.recyclerViewSongHistory.context, llmHistory.orientation)
        historyAndRecordsPagerAdapter.recyclerViewSongHistory.addItemDecoration(divider)

        trackHistoryViewModel = ViewModelProvider(this).get(TrackHistoryViewModel::class.java)
        trackHistoryViewModel.getAllHistoryPaged().observe(viewLifecycleOwner) { entries: PagedList<TrackHistoryEntry>? ->
            trackHistoryAdapter.submitList(entries)
        }

        recordingsAdapter = RecordingsAdapter(requireContext())
        recordingsAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val lm = historyAndRecordsPagerAdapter.recyclerViewRecordings.layoutManager as LinearLayoutManager
                if (lm.findFirstVisibleItemPosition() < 2) {
                    historyAndRecordsPagerAdapter.recyclerViewRecordings.scrollToPosition(0)
                }
            }
        })
        historyAndRecordsPagerAdapter.recyclerViewRecordings.adapter = recordingsAdapter
        val llmRecordings = LinearLayoutManager(context).apply { orientation = RecyclerView.VERTICAL }
        historyAndRecordsPagerAdapter.recyclerViewRecordings.layoutManager = llmRecordings
        historyAndRecordsPagerAdapter.recyclerViewRecordings.addItemDecoration(divider)

        val vto = pagerHistoryAndRecordings.viewTreeObserver
        if (vto.isAlive) {
            vto.addOnGlobalLayoutListener {
                val lp = pagerHistoryAndRecordings.layoutParams
                val newHeight = scrollViewContent.height
                if (newHeight != lp.height) {
                    lp.height = newHeight
                    pagerHistoryAndRecordings.layoutParams = lp
                }
            }
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnPlay.setOnClickListener {
            playerViewModel.playPause { station ->
                Utils.showPlaySelection(requireContext(), station, requireActivity().supportFragmentManager)
            }
        }

        btnPrev.setOnClickListener { playerViewModel.skipToPrevious() }
        btnNext.setOnClickListener { playerViewModel.skipToNext() }

        btnRecord.setOnClickListener {
            if (playerViewModel.uiState.value.isPlaying) {
                val startedOrStopped = playerViewModel.toggleRecording(
                    Utils.verifyStoragePermissions(this, PERM_REQ_STORAGE_RECORD)
                )
                if (startedOrStopped) {
                    pagerHistoryAndRecordings.setCurrentItem(1, true)
                }
            }
        }

        btnFavourite.setOnClickListener {
            playerViewModel.toggleFavorite()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    playerViewModel.uiState.collect { state ->
                        fullUpdate(state)
                    }
                }
                launch {
                    while (isActive) {
                        val state = playerViewModel.uiState.value
                        if (state.isPlaying) {
                            updateMetrics(state)
                            updateRunningRecording(state.isRecording, state.runningRecordingInfo)
                        }
                        delay(1000)
                    }
                }
            }
        }
    }

    fun init() {
        if (isAdded) fullUpdate(playerViewModel.uiState.value)
    }

    fun setTouchInterceptListener(l: TouchInterceptListener) { touchInterceptListener = l }

    fun resetScroll() {
        scrollViewContent.scrollTo(0, 0)
        historyAndRecordsPagerAdapter.recyclerViewSongHistory.scrollToPosition(0)
        historyAndRecordsPagerAdapter.recyclerViewRecordings.scrollToPosition(0)
    }

    fun isScrolled() = scrollViewContent.scrollY > 0

    private fun fullUpdate(state: PlayerUiState) {
        val station = state.station
        val liveInfo = state.streamLiveInfo
        if (station != null) {
            val streamTitle = liveInfo.title
            textViewGeneralInfo.text = if (!TextUtils.isEmpty(streamTitle)) streamTitle else station.Name

            val flag = CountryFlagsLoader.instance.getFlag(requireContext(), station.CountryCode)
            if (flag != null) {
                val k = flag.minimumWidth / flag.minimumHeight.toFloat()
                val viewHeight = artAndInfoPagerAdapter.textViewStationDescription.textSize
                flag.setBounds(0, 0, (k * viewHeight).toInt(), viewHeight.toInt())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                artAndInfoPagerAdapter.textViewStationDescription.setCompoundDrawablesRelative(flag, null, null, null)
            } else {
                @Suppress("DEPRECATION")
                artAndInfoPagerAdapter.textViewStationDescription.setCompoundDrawables(flag, null, null, null)
            }
            artAndInfoPagerAdapter.textViewStationDescription.text = com.github.fschroffner.radiodroid3.station.StationFormatter.getLongDetails(station, requireContext())
            artAndInfoPagerAdapter.viewTags.setTags(station.TagsAll.split(","))
        }

        updateAlbumArt(state)
        recordingsAdapter.setRecordings(state.savedRecordings)
        updateRunningRecording(state.isRecording, state.runningRecordingInfo)
        updatePlaybackButtons(state.isPlaying, state.isRecording)
        updateFavouriteButton(state.isFavorite)
        updateMetrics(state)
    }

    private fun updateAlbumArt(state: PlayerUiState) {
        val artUrl = state.albumArtUrl
        val station = state.station
        if (!artUrl.isNullOrEmpty()) {
            Picasso.get().load(artUrl).error(R.drawable.ic_launcher).into(artAndInfoPagerAdapter.imageViewArt)
        } else if (station != null && station.hasIcon()) {
            Picasso.get().load(station.IconUrl).error(R.drawable.ic_launcher).into(artAndInfoPagerAdapter.imageViewArt)
        } else {
            artAndInfoPagerAdapter.imageViewArt.setImageResource(R.drawable.ic_launcher)
        }
    }

    private fun updatePlaybackButtons(playing: Boolean, recording: Boolean) {
        updatePlayButton(playing)
        updateRecordButton(playing, recording)
    }

    private fun updatePlayButton(playing: Boolean) {
        if (playing) {
            btnPlay.setImageResource(R.drawable.ic_pause_circle)
            btnPlay.contentDescription = resources.getString(R.string.detail_pause)
        } else {
            btnPlay.setImageResource(R.drawable.ic_play_circle)
            btnPlay.contentDescription = resources.getString(R.string.detail_play)
        }
    }

    private fun updateRecordButton(playing: Boolean, recording: Boolean) {
        btnRecord.isEnabled = playing
        if (recording) {
            btnRecord.setImageResource(R.drawable.ic_stop_recording)
            btnRecord.contentDescription = resources.getString(R.string.detail_stop)
        } else {
            btnRecord.setImageResource(R.drawable.ic_start_recording)
            btnRecord.contentDescription = resources.getString(
                if (!storagePermissionsDenied) R.string.image_button_record else R.string.image_button_record_request_permission
            )
        }
    }

    private fun updateRunningRecording(isRecording: Boolean, recordingInfo: RunningRecordingInfo?) {
        if (isRecording && recordingInfo != null) {
            groupRecordings.visibility = View.VISIBLE
            imgRecordingIcon.startAnimation(AnimationUtils.loadAnimation(context, R.anim.blink_recording))
            textViewRecordingSize.text = Utils.getReadableBytes(recordingInfo.bytesWritten.toDouble())
            textViewRecordingName.text = recordingInfo.fileName
        } else {
            groupRecordings.visibility = View.GONE
            imgRecordingIcon.clearAnimation()
        }
    }

    private fun updateFavouriteButton(isFavorite: Boolean) {
        if (isFavorite) {
            btnFavourite.setImageResource(R.drawable.ic_star_24dp)
            btnFavourite.contentDescription = requireContext().applicationContext.getString(R.string.detail_unstar)
        } else {
            btnFavourite.setImageResource(R.drawable.ic_star_border_24dp)
            btnFavourite.contentDescription = requireContext().applicationContext.getString(R.string.detail_star)
        }
    }

    private fun updateMetrics(state: PlayerUiState) {
        val shoutcastInfo = state.shoutcastInfo
        if (state.isPlaying) {
            var networkUsageInfo = Utils.getReadableBytes(state.transferredBytes.toDouble())
            if (shoutcastInfo != null && shoutcastInfo.bitrate > 0) networkUsageInfo += " (${shoutcastInfo.bitrate} kbps)"
            textViewNetworkUsageInfo.text = networkUsageInfo

            val now = System.currentTimeMillis()
            val startTime = state.lastPlayStartTime
            val deltaSeconds = maxOf(if (startTime > 0) (now - startTime) / 1000 else 0, 0)
            textViewTimePlayed.text = DateUtils.formatElapsedTime(deltaSeconds)
            textViewTimeCached.text = DateUtils.formatElapsedTime(state.bufferedSeconds)
        }
    }

    private inner class ArtAndInfoPagerAdapter(context: Context, parent: ViewGroup) : PagerAdapter() {
        private val layoutAlbumArt: ViewGroup
        private val layoutStationInfo: ViewGroup
        private val titles: Array<String>

        val imageViewArt: ImageView
        val textViewStationDescription: TextView
        val viewTags: TagsView

        init {
            val inflater = LayoutInflater.from(context)
            layoutAlbumArt = inflater.inflate(R.layout.page_player_album_art, parent, false) as ViewGroup
            layoutStationInfo = inflater.inflate(R.layout.page_player_station_info, parent, false) as ViewGroup
            titles = arrayOf(resources.getString(R.string.tab_player_art), resources.getString(R.string.tab_player_info))
            imageViewArt = layoutAlbumArt.findViewById(R.id.imageViewArt)
            textViewStationDescription = layoutStationInfo.findViewById(R.id.textViewStationDescription)
            viewTags = layoutStationInfo.findViewById(R.id.viewTags)
        }

        override fun instantiateItem(collection: ViewGroup, position: Int): Any {
            val layout = if (position == 0) layoutAlbumArt else layoutStationInfo
            collection.addView(layout)
            return layout
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) { container.removeView(obj as View) }
        override fun getCount() = 2
        override fun isViewFromObject(view: View, obj: Any) = view == obj
        override fun getPageTitle(position: Int): CharSequence = titles[position]
    }

    private inner class HistoryAndRecordsPagerAdapter(context: Context, parent: ViewGroup) : PagerAdapter() {
        private val layoutSongHistory: ViewGroup
        private val layoutRecordings: ViewGroup
        private val titles: Array<String>

        val recyclerViewSongHistory: RecyclerView
        val recyclerViewRecordings: RecyclerView

        init {
            val inflater = LayoutInflater.from(context)
            layoutSongHistory = inflater.inflate(R.layout.page_player_history, parent, false) as ViewGroup
            layoutRecordings = inflater.inflate(R.layout.page_player_recordings, parent, false) as ViewGroup
            titles = arrayOf(resources.getString(R.string.tab_player_history), resources.getString(R.string.tab_player_recordings))
            recyclerViewSongHistory = layoutSongHistory.findViewById(R.id.recyclerViewSongHistory)
            recyclerViewRecordings = layoutRecordings.findViewById(R.id.recyclerViewRecordings)
        }

        override fun instantiateItem(collection: ViewGroup, position: Int): Any {
            val layout = if (position == 0) layoutSongHistory else layoutRecordings
            collection.addView(layout)
            return layout
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) { container.removeView(obj as View) }
        override fun getCount() = 2
        override fun isViewFromObject(view: View, obj: Any) = view == obj
        override fun getPageTitle(position: Int): CharSequence = titles[position]
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERM_REQ_STORAGE_RECORD) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                storagePermissionsDenied = false
                playerViewModel.toggleRecording(true)
            } else {
                storagePermissionsDenied = true
                Toast.makeText(activity, resources.getString(R.string.error_record_needs_write), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
