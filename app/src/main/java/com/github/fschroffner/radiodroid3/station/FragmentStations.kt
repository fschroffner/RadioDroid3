package com.github.fschroffner.radiodroid3.station

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.fschroffner.radiodroid3.data.loading.LoadingStateHolder
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.BuildConfig
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.StationSaveManager
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.databinding.FragmentStationsRemoteBinding
import com.github.fschroffner.radiodroid3.interfaces.IFragmentRefreshable
import com.github.fschroffner.radiodroid3.interfaces.IFragmentSearchable
import com.github.fschroffner.radiodroid3.utils.CustomFilter

/**
 * Browse/search list of remote stations.
 *
 * The browse path (a radio-browser endpoint passed via the "url" argument) is driven
 * by [StationsViewModel] + [StationRepository] instead of a per-fragment AsyncTask, so
 * loading survives configuration changes and the fragment only renders state. The
 * search path (used by the search tab) still delegates to the adapter's [StationsFilter].
 */
@AndroidEntryPoint
class FragmentStations : Fragment(), IFragmentSearchable, IFragmentRefreshable {

    companion object {
        private const val TAG = "FragmentStations"
        const val KEY_SEARCH_ENABLED = "SEARCH_ENABLED"
    }

    private val stationsViewModel: StationsViewModel by viewModels()

    private var _binding: FragmentStationsRemoteBinding? = null
    private val binding get() = _binding!!

    private var relativeUrl: String = ""
    private var searchEnabled = false
    private var stationsFilter: StationsFilter? = null
    private var lastSearchStyle = StationsFilter.SearchStyle.ByName
    private var lastQuery = ""
    private lateinit var queue: StationSaveManager

    private fun onStationClick(theStation: DataRadioStation, pos: Int) {
        Utils.showPlaySelection(requireContext(), theStation, requireActivity().supportFragmentManager)
    }

    /** hidebroken API parameter: the inverse of the "show_broken" preference. */
    private fun hideBroken() =
        !PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean("show_broken", false)

    private fun renderStations(stations: List<DataRadioStation>) {
        val b = _binding ?: return
        if (BuildConfig.DEBUG) Log.d(TAG, "rendering ${stations.size} stations.")

        val showBroken = !hideBroken()
        queue.clear()
        queue.addAll(stations)

        val filtered = stations.filter { showBroken || it.Working }
        val adapter = b.recyclerViewStations.adapter as? ItemAdapterStation ?: return
        adapter.updateList(null, filtered)
        if (searchEnabled) stationsFilter?.filter("")
    }

    private fun loadBrowse(forceUpdate: Boolean) {
        if (relativeUrl.isEmpty()) return
        if (context != null) {
            LoadingStateHolder.show()
        }
        stationsViewModel.load(relativeUrl, hideBroken(), forceUpdate)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                stationsViewModel.uiState.collect { state ->
                    val b = _binding ?: return@collect
                    when (state) {
                        is StationsUiState.Idle -> {
                            b.layoutError.visibility = View.GONE
                            b.swiperefresh.isRefreshing = false
                        }
                        is StationsUiState.Loading -> {
                            b.layoutError.visibility = View.GONE
                            b.swiperefresh.isRefreshing = true
                        }
                        is StationsUiState.Success -> {
                            b.layoutError.visibility = View.GONE
                            b.swiperefresh.isRefreshing = false
                            dismissLoadingIndicator()
                            renderStations(state.stations)
                        }
                        is StationsUiState.Error -> {
                            b.layoutError.visibility = View.VISIBLE
                            b.swiperefresh.isRefreshing = false
                            dismissLoadingIndicator()
                        }
                    }
                }
            }
        }
    }

    private fun dismissLoadingIndicator() {
        if (context != null) {
            LoadingStateHolder.hide()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Log.d("STATIONS", "onCreateView()")
        queue = StationSaveManager(requireContext())
        relativeUrl = arguments?.getString("url") ?: ""
        searchEnabled = arguments?.getBoolean(KEY_SEARCH_ENABLED, false) ?: false

        _binding = FragmentStationsRemoteBinding.inflate(inflater, container, false)
        val rvStations = binding.recyclerViewStations
        val layoutError = binding.layoutError
        val btnRetry = binding.btnRefresh

        val adapter = ItemAdapterStation(requireActivity(), R.layout.list_item_station, StationsFilter.FilterType.GLOBAL)
        adapter.setStationActionsListener(object : ItemAdapterStation.StationActionsListener {
            override fun onStationClick(station: DataRadioStation, pos: Int) {
                this@FragmentStations.onStationClick(station, pos)
            }
            override fun onStationSwiped(station: DataRadioStation) {}
            override fun onStationMoved(from: Int, to: Int) {}
            override fun onStationMoveFinished() {}
        })

        if (searchEnabled) {
            stationsFilter = adapter.getFilter()
            stationsFilter!!.setDelayer(object : CustomFilter.Delayer {
                private var previousLength = 0
                override fun getPostingDelay(constraint: CharSequence?): Long {
                    if (constraint == null) return 0
                    val delay = if (constraint.length < previousLength) 500L else 0L
                    previousLength = constraint.length
                    return delay
                }
            })
            adapter.setFilterListener { searchStatus ->
                val currentBinding = _binding ?: return@setFilterListener
                currentBinding.layoutError.visibility = if (searchStatus == StationsFilter.SearchStatus.ERROR) View.VISIBLE else View.GONE
                LoadingStateHolder.hide()
                currentBinding.swiperefresh.isRefreshing = false
            }
            btnRetry.setOnClickListener { Search(lastSearchStyle, lastQuery) }
        }

        val llm = LinearLayoutManager(context).apply { orientation = LinearLayoutManager.VERTICAL }
        rvStations.layoutManager = llm
        rvStations.adapter = adapter
        rvStations.addItemDecoration(DividerItemDecoration(rvStations.context, llm.orientation))

        binding.swiperefresh.setOnRefreshListener {
            if (relativeUrl.isNotEmpty()) {
                loadBrowse(true)
            } else if (searchEnabled) {
                stationsFilter?.clearList()
                Search(lastSearchStyle, lastQuery)
            }
        }

        observeViewModel()

        if (relativeUrl.isNotEmpty()) {
            loadBrowse(false)
        }

        if (lastQuery.isNotEmpty() && stationsFilter != null) {
            Log.d("STATIONS", "do queued search for: $lastQuery style=$lastSearchStyle")
            stationsFilter!!.clearList()
            Search(lastSearchStyle, lastQuery)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.recyclerViewStations?.adapter = null
        _binding = null
    }

    override fun Search(searchStyle: StationsFilter.SearchStyle, query: String) {
        Log.d("STATIONS", "query = $query searchStyle=$searchStyle")
        lastQuery = query
        lastSearchStyle = searchStyle

        if (_binding != null && searchEnabled) {
            Log.d("STATIONS", "query a = $query")
            if (!TextUtils.isEmpty(query)) {
                LoadingStateHolder.show()
            }
            stationsFilter!!.setSearchStyle(searchStyle)
            stationsFilter!!.filter(query)
        } else {
            Log.d("STATIONS", "query b = $query $searchEnabled ")
        }
    }

    override fun Refresh() {
        if (relativeUrl.isNotEmpty()) {
            loadBrowse(true)
        } else if (searchEnabled) {
            stationsFilter?.clearList()
            Search(lastSearchStyle, lastQuery)
        }
    }
}
