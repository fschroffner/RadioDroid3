package com.github.fschroffner.radiodroid3

import android.content.SharedPreferences
import android.os.Bundle
import android.util.DisplayMetrics
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
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.fschroffner.radiodroid3.databinding.FragmentStationsBinding
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.FavoritesViewModel
import com.github.fschroffner.radiodroid3.station.ItemAdapterIconOnlyStation
import com.github.fschroffner.radiodroid3.station.ItemAdapterStation
import com.github.fschroffner.radiodroid3.station.StationActions
import com.github.fschroffner.radiodroid3.station.StationsFilter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FragmentStarred : Fragment() {
    private val TAG = "FragmentStarred"

    private val favoritesViewModel: FavoritesViewModel by viewModels()

    private var _binding: FragmentStationsBinding? = null
    private val binding get() = _binding!!

    private fun onStationClick(station: DataRadioStation) {
        Utils.showPlaySelection(requireContext(), station, requireActivity().supportFragmentManager)
    }

    fun RefreshListGui() {
        val adapter = _binding?.recyclerViewStations?.adapter as? ItemAdapterStation ?: return
        adapter.updateList(null, favoritesViewModel.uiState.value.stations)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStationsBinding.inflate(inflater, container, false)
        val rvStations = binding.recyclerViewStations

        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val adapter: ItemAdapterStation
        if (prefs.getBoolean("load_icons", false) && prefs.getBoolean("icons_only_favorites_style", false)) {
            adapter = ItemAdapterIconOnlyStation(requireActivity(), R.layout.list_item_icon_only_station, StationsFilter.FilterType.LOCAL)
            val ctx = requireContext()
            val displayMetrics: DisplayMetrics = ctx.resources.displayMetrics
            val itemWidth = ctx.resources.getDimension(R.dimen.regular_style_icon_container_width).toInt()
            val noOfColumns = displayMetrics.widthPixels / itemWidth
            rvStations.adapter = adapter
            rvStations.layoutManager = GridLayoutManager(ctx, noOfColumns)
            (adapter as ItemAdapterIconOnlyStation).enableItemMove(rvStations)
        } else {
            adapter = ItemAdapterStation(requireActivity(), R.layout.list_item_station, StationsFilter.FilterType.LOCAL)
            val llm = LinearLayoutManager(requireContext()).apply { orientation = RecyclerView.VERTICAL }
            rvStations.adapter = adapter
            rvStations.layoutManager = llm
            rvStations.addItemDecoration(DividerItemDecoration(rvStations.context, llm.orientation))
            adapter.enableItemMoveAndRemoval(rvStations)
        }

        adapter.setStationActionsListener(object : ItemAdapterStation.StationActionsListener {
            override fun onStationClick(station: DataRadioStation, pos: Int) {
                this@FragmentStarred.onStationClick(station)
            }
            override fun onStationSwiped(station: DataRadioStation) {
                StationActions.removeFromFavourites(requireContext(), binding.root, station)
            }
            override fun onStationMoved(from: Int, to: Int) {
                favoritesViewModel.moveStation(from, to)
            }
            override fun onStationMoveFinished() {
                requireView().post {
                    favoritesViewModel.saveOrder()
                }
            }
        })

        binding.swiperefresh.setOnRefreshListener {
            if (BuildConfig.DEBUG) Log.d(TAG, "onRefresh called from SwipeRefreshLayout")
            favoritesViewModel.refresh()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                favoritesViewModel.uiState.collect { state ->
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
}
