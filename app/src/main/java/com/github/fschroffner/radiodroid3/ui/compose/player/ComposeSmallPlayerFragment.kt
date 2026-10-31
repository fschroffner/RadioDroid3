package com.github.fschroffner.radiodroid3.ui.compose.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.FragmentPlayerSmall
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.player.PlayerViewModel
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.StationActions
import com.github.fschroffner.radiodroid3.ui.compose.theme.RadioDroidTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Modern Jetpack Compose implementation of the Mini Player Fragment.
 *
 * Demonstrates clean interoperability via [ComposeView], reducing XML boilerplate
 * while seamlessly consuming [PlayerViewModel.uiState].
 */
@AndroidEntryPoint
class ComposeSmallPlayerFragment : Fragment() {

    private val viewModel: PlayerViewModel by activityViewModels()
    private var callback: FragmentPlayerSmall.Callback? = null

    fun setCallback(callback: FragmentPlayerSmall.Callback) {
        this.callback = callback
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                RadioDroidTheme {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                    ComposePlayerBar(
                        uiState = uiState,
                        onTogglePlayer = { callback?.onToggle() },
                        onPlayPauseClick = {
                            viewModel.playPause { station ->
                                viewModel.showPlaySelection(
                                    requireContext(),
                                    station,
                                    requireActivity().supportFragmentManager
                                )
                            }
                        },
                        onMoreClick = {
                            val station = uiState.station ?: viewModel.getCurrentOrLastStation()
                            if (station != null) {
                                showPlayerMenu(this, station, uiState.isFavorite)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun showPlayerMenu(anchorView: View, station: DataRadioStation, isFavorite: Boolean) {
        val popup = PopupMenu(requireContext(), anchorView)
        val menu = popup.menu

        val favTitle = if (isFavorite) R.string.action_unstar else R.string.action_star
        menu.add(0, 1, 0, favTitle)
        menu.add(0, 2, 1, R.string.action_share)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    if (isFavorite) {
                        viewModel.favouriteManager.remove(station.StationUuid)
                    } else {
                        viewModel.favouriteManager.add(station)
                    }
                    true
                }
                2 -> {
                    StationActions.shareStation(requireContext(), station)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}
