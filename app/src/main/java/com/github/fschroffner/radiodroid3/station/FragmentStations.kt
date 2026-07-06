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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.BuildConfig
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.RadioDroidApp
import com.github.fschroffner.radiodroid3.StationSaveManager
import com.github.fschroffner.radiodroid3.Utils
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

    private lateinit var rvStations: RecyclerView
    private lateinit var layoutError: ViewGroup
    private lateinit var btnRetry: MaterialButton
    private var swipeRefreshLayout: SwipeRefreshLayout? = null

    private var relativeUrl: String = ""
    private var searchEnabled = false
    private var stationsFilter: StationsFilter? = null
    private var lastSearchStyle = StationsFilter.SearchStyle.ByName
    private var lastQuery = ""
    private lateinit var queue: StationSaveManager

    private fun onStationClick(theStation: DataRadioStation, pos: Int) {
        val app = requireActivity().application as RadioDroidApp
        Utils.showPlaySelection(app, theStation, requireActivity().supportFragmentManager)
    }

    /** hidebroken API parameter: the inverse of the "show_broken" preference. */
    private fun hideBroken() =
        !PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean("show_broken", false)

    private fun renderStations(stations: List<DataRadioStation>) {
        if (!::rvStations.isInitialized) return
        if (BuildConfig.DEBUG) Log.d(TAG, "rendering ${stations.size} stations.")

        val showBroken = !hideBroken()
        queue.clear()
        queue.addAll(stations)

        val filtered = stations.filter { showBroken || it.Working }
        val adapter = rvStations.adapter as? ItemAdapterStation ?: return
        adapter.updateList(null, filtered)
        if (searchEnabled) stationsFilter?.filter("")
    }

    private fun loadBrowse(forceUpdate: Boolean) {
        if (relativeUrl.isEmpty()) return
        if (context != null) {
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))
        }
        stationsViewModel.load(relativeUrl, hideBroken(), forceUpdate)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                stationsViewModel.uiState.collect { state ->
                    when (state) {
                        is StationsUiState.Success -> {
                            renderStations(state.stations)
                            onLoadFinished()
                        }
                        is StationsUiState.Error -> {
                            try {
                                android.widget.Toast.makeText(
                                    context, resources.getText(R.string.error_list_update),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Log.e(TAG, "$e")
                            }
                            onLoadFinished()
                        }
                        StationsUiState.Loading, StationsUiState.Idle -> {}
                    }
                }
            }
        }
    }

    private fun onLoadFinished() {
        swipeRefreshLayout?.isRefreshing = false
        if (context != null) {
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d("STATIONS", "onCreateView()")
        queue = StationSaveManager(requireContext())
        relativeUrl = arguments?.getString("url") ?: ""
        searchEnabled = arguments?.getBoolean(KEY_SEARCH_ENABLED, false) ?: false

        val view = inflater.inflate(R.layout.fragment_stations_remote, container, false)
        rvStations = view.findViewById(R.id.recyclerViewStations)
        layoutError = view.findViewById(R.id.layoutError)
        btnRetry = view.findViewById(R.id.btnRefresh)

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
                layoutError.visibility = if (searchStatus == StationsFilter.SearchStatus.ERROR) View.VISIBLE else View.GONE
                LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))
                swipeRefreshLayout?.isRefreshing = false
            }
            btnRetry.setOnClickListener { Search(lastSearchStyle, lastQuery) }
        }

        val llm = LinearLayoutManager(context).apply { orientation = LinearLayoutManager.VERTICAL }
        rvStations.layoutManager = llm
        rvStations.adapter = adapter
        rvStations.addItemDecoration(DividerItemDecoration(rvStations.context, llm.orientation))

        swipeRefreshLayout = view.findViewById(R.id.swiperefresh)
        swipeRefreshLayout!!.setOnRefreshListener {
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

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rvStations.adapter = null
    }

    override fun Search(searchStyle: StationsFilter.SearchStyle, query: String) {
        Log.d("STATIONS", "query = $query searchStyle=$searchStyle")
        lastQuery = query
        lastSearchStyle = searchStyle

        if (::rvStations.isInitialized && searchEnabled) {
            Log.d("STATIONS", "query a = $query")
            if (!TextUtils.isEmpty(query)) {
                LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))
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
