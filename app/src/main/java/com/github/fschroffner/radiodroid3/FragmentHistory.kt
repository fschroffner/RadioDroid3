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
import com.google.android.material.snackbar.Snackbar
import com.github.fschroffner.radiodroid3.databinding.FragmentStationsBinding
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

    private var _binding: FragmentStationsBinding? = null
    private val binding get() = _binding!!

    private fun onStationClick(station: DataRadioStation) {
        Utils.showPlaySelection(requireContext(), station, requireActivity().supportFragmentManager)
        _binding?.recyclerViewStations?.smoothScrollToPosition(0)
    }

    fun RefreshListGui() {
        val adapter = _binding?.recyclerViewStations?.adapter as? ItemAdapterStation ?: return
        adapter.updateList(null, historyViewModel.uiState.value.stations)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStationsBinding.inflate(inflater, container, false)
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

        val llm = LinearLayoutManager(context).apply { orientation = LinearLayoutManager.VERTICAL }
        binding.recyclerViewStations.adapter = adapter
        binding.recyclerViewStations.layoutManager = llm
        binding.recyclerViewStations.addItemDecoration(DividerItemDecoration(binding.recyclerViewStations.context, llm.orientation))
        adapter.enableItemRemoval(binding.recyclerViewStations)

        binding.swiperefresh.setOnRefreshListener {
            if (BuildConfig.DEBUG) Log.d(TAG, "onRefresh called from SwipeRefreshLayout")
            historyViewModel.refresh()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                historyViewModel.uiState.collect { state ->
                    _binding?.swiperefresh?.isRefreshing = state.isRefreshing
                    if (state.errorMessage != null && context != null) {
                        Toast.makeText(context, state.errorMessage, Toast.LENGTH_SHORT).show()
                    }
                    val adapter = _binding?.recyclerViewStations?.adapter as? ItemAdapterStation ?: return@collect
                    adapter.updateList(null, state.stations)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}