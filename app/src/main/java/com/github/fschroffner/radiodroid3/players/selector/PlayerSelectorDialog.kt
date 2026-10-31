package com.github.fschroffner.radiodroid3.players.selector

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.github.fschroffner.radiodroid3.CastHandler
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.player.MediaControllerRepository
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerSelectorDialog(
    private val stationToPlay: DataRadioStation? = null
) : BottomSheetDialogFragment() {

    @Inject lateinit var mediaControllerRepository: MediaControllerRepository
    @Inject lateinit var castHandler: CastHandler
    @Inject lateinit var stationPlaybackUseCase: com.github.fschroffner.radiodroid3.player.StationPlaybackUseCase

    private lateinit var recyclerViewPlayers: RecyclerView
    private lateinit var playerSelectorAdapter: PlayerSelectorAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_player_selector, container, false)

        recyclerViewPlayers = view.findViewById(R.id.recyclerViewPlayers)
        recyclerViewPlayers.layoutManager = GridLayoutManager(context, 2, RecyclerView.VERTICAL, false)

        playerSelectorAdapter = PlayerSelectorAdapter(
            context = requireContext(),
            stationToPlay = stationToPlay,
            mediaControllerRepository = mediaControllerRepository,
            castHandler = castHandler,
            stationPlaybackUseCase = stationPlaybackUseCase
        )
        recyclerViewPlayers.adapter = playerSelectorAdapter

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mediaControllerRepository.playbackState.collect {
                    playerSelectorAdapter.notifyRadioDroidPlaybackStateChanged()
                }
            }
        }
    }

    companion object {
        const val FRAGMENT_TAG = "player_selector_dialog_fragment"
    }
}
