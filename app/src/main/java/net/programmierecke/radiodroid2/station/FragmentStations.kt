package net.programmierecke.radiodroid2.station

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import net.programmierecke.radiodroid2.ActivityMain
import net.programmierecke.radiodroid2.BuildConfig
import net.programmierecke.radiodroid2.FragmentBase
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.RadioDroidApp
import net.programmierecke.radiodroid2.StationSaveManager
import net.programmierecke.radiodroid2.Utils
import net.programmierecke.radiodroid2.interfaces.IFragmentSearchable
import net.programmierecke.radiodroid2.utils.CustomFilter

class FragmentStations : FragmentBase(), IFragmentSearchable {

    companion object {
        private const val TAG = "FragmentStations"
        const val KEY_SEARCH_ENABLED = "SEARCH_ENABLED"
    }

    private lateinit var rvStations: RecyclerView
    private lateinit var layoutError: ViewGroup
    private lateinit var btnRetry: MaterialButton
    private var swipeRefreshLayout: SwipeRefreshLayout? = null

    private var searchEnabled = false
    private var stationsFilter: StationsFilter? = null
    private var lastSearchStyle = StationsFilter.SearchStyle.ByName
    private var lastQuery = ""
    private lateinit var queue: StationSaveManager

    private fun onStationClick(theStation: DataRadioStation, pos: Int) {
        val app = requireActivity().application as RadioDroidApp
        Utils.showPlaySelection(app, theStation, requireActivity().supportFragmentManager)
    }

    override fun RefreshListGui() {
        if (!::rvStations.isInitialized || !hasUrl()) return
        if (BuildConfig.DEBUG) Log.d(TAG, "refreshing the stations list.")

        val ctx = context ?: return
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx)
        val showBroken = sharedPref.getBoolean("show_broken", false)

        val radioStations = DataRadioStation.DecodeJson(urlResult)
        queue.clear()
        queue.addAll(radioStations)

        if (BuildConfig.DEBUG) Log.d(TAG, "station count:${radioStations.size}")

        val filtered = radioStations.filter { showBroken || it.Working }
        val adapter = rvStations.adapter as? ItemAdapterStation ?: return
        adapter.updateList(null, filtered)
        if (searchEnabled) stationsFilter?.filter("")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d("STATIONS", "onCreateView()")
        queue = StationSaveManager(requireContext())
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
            if (hasUrl()) {
                DownloadUrl(true, false)
            } else if (searchEnabled) {
                stationsFilter?.clearList()
                Search(lastSearchStyle, lastQuery)
            }
        }

        RefreshListGui()

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

    override fun DownloadFinished() {
        swipeRefreshLayout?.isRefreshing = false
    }
}
