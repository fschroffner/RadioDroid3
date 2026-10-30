package com.github.fschroffner.radiodroid3

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
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
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.views.RecyclerAwareNestedScrollView
import com.github.fschroffner.radiodroid3.views.TagsView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FragmentPlayerFull : Fragment() {

    interface TouchInterceptListener {
        fun requestDisallowInterceptTouchEvent(disallow: Boolean)
    }

    private var touchInterceptListener: TouchInterceptListener? = null
    private val playerViewModel: PlayerViewModel by activityViewModels()

    private lateinit var trackHistoryAdapter: TrackHistoryAdapter

    private lateinit var scrollViewContent: RecyclerAwareNestedScrollView
    private lateinit var pagerArtAndInfo: ViewPager
    private lateinit var artAndInfoPagerAdapter: ArtAndInfoPagerAdapter
    private lateinit var textViewGeneralInfo: TextView
    private lateinit var textViewTimePlayed: TextView
    private lateinit var textViewNetworkUsageInfo: TextView
    private lateinit var textViewTimeCached: TextView
    private lateinit var pagerHistory: ViewPager
    private lateinit var historyPagerAdapter: HistoryPagerAdapter
    private lateinit var trackHistoryViewModel: TrackHistoryViewModel
    private lateinit var btnPlay: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnFavourite: ImageButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        trackHistoryAdapter = TrackHistoryAdapter(requireActivity())
        trackHistoryAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val lm = historyPagerAdapter.recyclerViewSongHistory.layoutManager as LinearLayoutManager
                if (lm.findFirstVisibleItemPosition() < 2) {
                    historyPagerAdapter.recyclerViewSongHistory.scrollToPosition(0)
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

        pagerHistory = view.findViewById(R.id.pagerHistory)
        historyPagerAdapter = HistoryPagerAdapter(requireContext(), pagerHistory)
        pagerHistory.adapter = historyPagerAdapter

        btnPlay = view.findViewById(R.id.buttonPlay)
        btnPrev = view.findViewById(R.id.buttonPrev)
        btnNext = view.findViewById(R.id.buttonNext)
        btnFavourite = view.findViewById(R.id.buttonFavorite)

        historyPagerAdapter.recyclerViewSongHistory.adapter = trackHistoryAdapter
        val llmHistory = LinearLayoutManager(context).apply { orientation = RecyclerView.VERTICAL }
        historyPagerAdapter.recyclerViewSongHistory.layoutManager = llmHistory
        val divider = DividerItemDecoration(historyPagerAdapter.recyclerViewSongHistory.context, llmHistory.orientation)
        historyPagerAdapter.recyclerViewSongHistory.addItemDecoration(divider)

        trackHistoryViewModel = ViewModelProvider(this).get(TrackHistoryViewModel::class.java)
        trackHistoryViewModel.getAllHistoryPaged().observe(viewLifecycleOwner) { entries: PagedList<TrackHistoryEntry>? ->
            trackHistoryAdapter.submitList(entries)
        }

        val vto = pagerHistory.viewTreeObserver
        if (vto.isAlive) {
            vto.addOnGlobalLayoutListener {
                val lp = pagerHistory.layoutParams
                val newHeight = scrollViewContent.height
                if (newHeight != lp.height) {
                    lp.height = newHeight
                    pagerHistory.layoutParams = lp
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
        historyPagerAdapter.recyclerViewSongHistory.scrollToPosition(0)
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
        updatePlayButton(state.isPlaying)
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

    private fun updatePlayButton(playing: Boolean) {
        if (playing) {
            btnPlay.setImageResource(R.drawable.ic_pause_circle)
            btnPlay.contentDescription = resources.getString(R.string.detail_pause)
        } else {
            btnPlay.setImageResource(R.drawable.ic_play_circle)
            btnPlay.contentDescription = resources.getString(R.string.detail_play)
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

    private inner class HistoryPagerAdapter(context: Context, parent: ViewGroup) : PagerAdapter() {
        private val layoutSongHistory: ViewGroup
        private val titles: Array<String>

        val recyclerViewSongHistory: RecyclerView

        init {
            val inflater = LayoutInflater.from(context)
            layoutSongHistory = inflater.inflate(R.layout.page_player_history, parent, false) as ViewGroup
            titles = arrayOf(resources.getString(R.string.tab_player_history))
            recyclerViewSongHistory = layoutSongHistory.findViewById(R.id.recyclerViewSongHistory)
        }

        override fun instantiateItem(collection: ViewGroup, position: Int): Any {
            collection.addView(layoutSongHistory)
            return layoutSongHistory
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) { container.removeView(obj as View) }
        override fun getCount() = 1
        override fun isViewFromObject(view: View, obj: Any) = view == obj
        override fun getPageTitle(position: Int): CharSequence = titles[position]
    }
}
