package com.github.fschroffner.radiodroid3

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.HistoryViewModel
import com.github.fschroffner.radiodroid3.station.ItemAdapterStation
import com.github.fschroffner.radiodroid3.station.StationsFilter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FragmentHistory : Fragment() {
    private val TAG = "FragmentHistory"

    private val historyViewModel: HistoryViewModel by viewModels()

    private lateinit var rvStations: RecyclerView
    private var swipeRefreshLayout: SwipeRefreshLayout? = null

    private fun onStationClick(station: DataRadioStation) {
        Utils.showPlaySelection(requireContext(), station, requireActivity().supportFragmentManager)
        rvStations.smoothScrollToPosition(0)
    }

    fun RefreshListGui() {
        if (!::rvStations.isInitialized) return
        val adapter = rvStations.adapter as? ItemAdapterStation ?: return
        adapter.updateList(null, historyViewModel.uiState.value.stations)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val adapter = ItemAdapterStation(requireActivity(), R.layout.list_item_station, StationsFilter.FilterType.LOCAL)
        adapter.setStationActionsListener(object : ItemAdapterStation.StationActionsListener {
            override fun onStationClick(station: DataRadioStation, pos: Int) {
                this@FragmentHistory.onStationClick(station)
            }
            override fun onStationSwiped(station: DataRadioStation) {
                val removedIdx = historyViewModel.removeStation(station)
                val snackbar = Snackbar.make(requireView(), R.string.notify_station_removed_from_list, 6000)
                val anchorView = requireView().rootView.findViewById<View>(R.id.bottom_sheet)
                    ?: requireView().rootView.findViewById<View>(R.id.fragment_player_small)
                if (anchorView != null) {
                    snackbar.anchorView = anchorView
                }
                snackbar.setAction(R.string.action_station_removed_from_list_undo) {
                    historyViewModel.restoreStation(station, removedIdx)
                }
                snackbar.show()
            }
            override fun onStationMoved(from: Int, to: Int) {}
            override fun onStationMoveFinished() {}
        })

        val view = inflater.inflate(R.layout.fragment_stations, container, false)
        val llm = LinearLayoutManager(context).apply { orientation = LinearLayoutManager.VERTICAL }
        rvStations = view.findViewById(R.id.recyclerViewStations)
        rvStations.adapter = adapter
        rvStations.layoutManager = llm
        rvStations.addItemDecoration(DividerItemDecoration(rvStations.context, llm.orientation))
        adapter.enableItemRemoval(rvStations)

        swipeRefreshLayout = view.findViewById(R.id.swiperefresh)
        swipeRefreshLayout?.setOnRefreshListener {
            if (BuildConfig.DEBUG) Log.d(TAG, "onRefresh called from SwipeRefreshLayout")
            historyViewModel.refresh()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                historyViewModel.uiState.collect { state ->
                    swipeRefreshLayout?.isRefreshing = state.isRefreshing
                    if (state.errorMessage != null && context != null) {
                        Toast.makeText(context, state.errorMessage, Toast.LENGTH_SHORT).show()
                    }
                    val adapter = rvStations.adapter as? ItemAdapterStation ?: return@collect
                    adapter.updateList(null, state.stations)
                }
            }
        }
    }
}
