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
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import coil.load
import com.github.fschroffner.radiodroid3.databinding.LayoutPlayerFullBinding
import com.github.fschroffner.radiodroid3.databinding.PagePlayerAlbumArtBinding
import com.github.fschroffner.radiodroid3.databinding.PagePlayerHistoryBinding
import com.github.fschroffner.radiodroid3.databinding.PagePlayerStationInfoBinding
import com.github.fschroffner.radiodroid3.history.TrackHistoryAdapter
import com.github.fschroffner.radiodroid3.history.TrackHistoryViewModel
import com.github.fschroffner.radiodroid3.player.PlayerUiState
import com.github.fschroffner.radiodroid3.player.PlayerViewModel
import com.github.fschroffner.radiodroid3.views.TagsView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FragmentPlayerFull : Fragment() {

    interface TouchInterceptListener {
        fun requestDisallowInterceptTouchEvent(disallow: Boolean)
    }

    private var touchInterceptListener: TouchInterceptListener? = null
    private val playerViewModel: PlayerViewModel by activityViewModels()
    private val trackHistoryViewModel: TrackHistoryViewModel by viewModels()

    private var _binding: LayoutPlayerFullBinding? = null
    private val binding get() = _binding!!

    private lateinit var trackHistoryAdapter: TrackHistoryAdapter
    private lateinit var artAndInfoPagerAdapter: ArtAndInfoPagerAdapter
    private lateinit var historyPagerAdapter: HistoryPagerAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LayoutPlayerFullBinding.inflate(inflater, container, false)

        trackHistoryAdapter = TrackHistoryAdapter(requireActivity())
        trackHistoryAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == 0) {
                    val lm = historyPagerAdapter.recyclerViewSongHistory.layoutManager as? LinearLayoutManager
                    if (lm != null && lm.findFirstVisibleItemPosition() < 2) {
                        historyPagerAdapter.recyclerViewSongHistory.scrollToPosition(0)
                    }
                }
            }
        })

        artAndInfoPagerAdapter = ArtAndInfoPagerAdapter(requireContext(), binding.pagerArtAndInfo)
        binding.pagerArtAndInfo.adapter = artAndInfoPagerAdapter

        binding.pagerArtAndInfo.setOnTouchListener(object : View.OnTouchListener {
            private val DRAG_THRESHOLD = 30
            private var downX = 0
            private var downY = 0
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val b = _binding ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX.toInt()
                        downY = event.rawY.toInt()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = Math.abs(event.rawX.toInt() - downX)
                        val dy = Math.abs(event.rawY.toInt() - downY)
                        if (dx > dy && dx > DRAG_THRESHOLD) {
                            b.pagerArtAndInfo.parent.requestDisallowInterceptTouchEvent(true)
                            b.scrollViewContent.parent.requestDisallowInterceptTouchEvent(false)
                            touchInterceptListener?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        b.scrollViewContent.parent.requestDisallowInterceptTouchEvent(false)
                        b.pagerArtAndInfo.parent.requestDisallowInterceptTouchEvent(false)
                        touchInterceptListener?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                return false
            }
        })

        historyPagerAdapter = HistoryPagerAdapter(requireContext(), binding.pagerHistory)
        binding.pagerHistory.adapter = historyPagerAdapter

        historyPagerAdapter.recyclerViewSongHistory.adapter = trackHistoryAdapter
        val llmHistory = LinearLayoutManager(context).apply { orientation = RecyclerView.VERTICAL }
        historyPagerAdapter.recyclerViewSongHistory.layoutManager = llmHistory
        val divider = DividerItemDecoration(historyPagerAdapter.recyclerViewSongHistory.context, llmHistory.orientation)
        historyPagerAdapter.recyclerViewSongHistory.addItemDecoration(divider)

        val vto = binding.pagerHistory.viewTreeObserver
        if (vto.isAlive) {
            vto.addOnGlobalLayoutListener {
                if (_binding != null) {
                    val lp = binding.pagerHistory.layoutParams
                    val newHeight = binding.scrollViewContent.height
                    if (newHeight != lp.height) {
                        lp.height = newHeight
                        binding.pagerHistory.layoutParams = lp
                    }
                }
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonPlay.setOnClickListener {
            playerViewModel.playPause { station ->
                playerViewModel.showPlaySelection(requireContext(), station, requireActivity().supportFragmentManager)
            }
        }

        binding.buttonPrev.setOnClickListener { playerViewModel.skipToPrevious() }
        binding.buttonNext.setOnClickListener { playerViewModel.skipToNext() }

        binding.buttonFavorite.setOnClickListener {
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
                    trackHistoryViewModel.historyFlow.collectLatest { pagingData ->
                        trackHistoryAdapter.submitData(pagingData)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun init() {
        if (isAdded && _binding != null) fullUpdate(playerViewModel.uiState.value)
    }

    fun setTouchInterceptListener(l: TouchInterceptListener) { touchInterceptListener = l }

    fun resetScroll() {
        _binding?.scrollViewContent?.scrollTo(0, 0)
        if (::historyPagerAdapter.isInitialized) {
            historyPagerAdapter.recyclerViewSongHistory.scrollToPosition(0)
        }
    }

    fun isScrolled(): Boolean = (_binding?.scrollViewContent?.scrollY ?: 0) > 0

    private fun fullUpdate(state: PlayerUiState) {
        val b = _binding ?: return
        val station = state.station
        val liveInfo = state.streamLiveInfo
        if (station != null) {
            val streamTitle = liveInfo.title
            b.textViewGeneralInfo.text = if (!TextUtils.isEmpty(streamTitle)) streamTitle else station.Name

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
        val artUrl = state.albumArtUrl ?: state.domainStation?.iconUrl ?: state.station?.IconUrl
        if (!artUrl.isNullOrEmpty()) {
            artAndInfoPagerAdapter.imageViewArt.load(artUrl) {
                error(R.drawable.ic_launcher)
                crossfade(true)
            }
        } else {
            artAndInfoPagerAdapter.imageViewArt.setImageResource(R.drawable.ic_launcher)
        }
    }

    private fun updatePlayButton(playing: Boolean) {
        val b = _binding ?: return
        if (playing) {
            b.buttonPlay.setImageResource(R.drawable.ic_pause_circle)
            b.buttonPlay.contentDescription = resources.getString(R.string.detail_pause)
        } else {
            b.buttonPlay.setImageResource(R.drawable.ic_play_circle)
            b.buttonPlay.contentDescription = resources.getString(R.string.detail_play)
        }
    }

    private fun updateFavouriteButton(isFavorite: Boolean) {
        val b = _binding ?: return
        if (isFavorite) {
            b.buttonFavorite.setImageResource(R.drawable.ic_star_24dp)
            b.buttonFavorite.contentDescription = requireContext().applicationContext.getString(R.string.detail_unstar)
        } else {
            b.buttonFavorite.setImageResource(R.drawable.ic_star_border_24dp)
            b.buttonFavorite.contentDescription = requireContext().applicationContext.getString(R.string.detail_star)
        }
    }

    private fun updateMetrics(state: PlayerUiState) {
        val b = _binding ?: return
        val shoutcastInfo = state.shoutcastInfo
        if (state.isPlaying) {
            var networkUsageInfo = Utils.getReadableBytes(state.transferredBytes.toDouble())
            if (shoutcastInfo != null && shoutcastInfo.bitrate > 0) networkUsageInfo += " (${shoutcastInfo.bitrate} kbps)"
            b.textViewNetworkUsageInfo.text = networkUsageInfo

            val now = System.currentTimeMillis()
            val startTime = state.lastPlayStartTime
            val deltaSeconds = maxOf(if (startTime > 0) (now - startTime) / 1000 else 0, 0)
            b.textViewTimePlayed.text = DateUtils.formatElapsedTime(deltaSeconds)
            b.textViewTimeCached.text = DateUtils.formatElapsedTime(state.bufferedSeconds)
        }
    }

    private inner class ArtAndInfoPagerAdapter(context: Context, parent: ViewGroup) : PagerAdapter() {
        val bindingArt: PagePlayerAlbumArtBinding
        val bindingInfo: PagePlayerStationInfoBinding
        private val titles: Array<String>

        val imageViewArt: ImageView get() = bindingArt.imageViewArt
        val textViewStationDescription: TextView get() = bindingInfo.textViewStationDescription
        val viewTags: TagsView get() = bindingInfo.viewTags

        init {
            val inflater = LayoutInflater.from(context)
            bindingArt = PagePlayerAlbumArtBinding.inflate(inflater, parent, false)
            bindingInfo = PagePlayerStationInfoBinding.inflate(inflater, parent, false)
            titles = arrayOf(resources.getString(R.string.tab_player_art), resources.getString(R.string.tab_player_info))
        }

        override fun instantiateItem(collection: ViewGroup, position: Int): Any {
            val view = if (position == 0) bindingArt.root else bindingInfo.root
            collection.addView(view)
            return view
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) { container.removeView(obj as View) }
        override fun getCount() = 2
        override fun isViewFromObject(view: View, obj: Any) = view == obj
        override fun getPageTitle(position: Int): CharSequence = titles[position]
    }

    private inner class HistoryPagerAdapter(context: Context, parent: ViewGroup) : PagerAdapter() {
        val bindingHistory: PagePlayerHistoryBinding
        private val titles: Array<String>

        val recyclerViewSongHistory: RecyclerView get() = bindingHistory.recyclerViewSongHistory

        init {
            val inflater = LayoutInflater.from(context)
            bindingHistory = PagePlayerHistoryBinding.inflate(inflater, parent, false)
            titles = arrayOf(resources.getString(R.string.tab_player_history))
        }

        override fun instantiateItem(collection: ViewGroup, position: Int): Any {
            collection.addView(bindingHistory.root)
            return bindingHistory.root
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) { container.removeView(obj as View) }
        override fun getCount() = 1
        override fun isViewFromObject(view: View, obj: Any) = view == obj
        override fun getPageTitle(position: Int): CharSequence = titles[position]
    }
}