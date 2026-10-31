package com.github.fschroffner.radiodroid3

import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.github.fschroffner.radiodroid3.databinding.LayoutPlayerSmallBinding
import com.github.fschroffner.radiodroid3.history.TrackHistoryRepository
import com.github.fschroffner.radiodroid3.player.PlayerUiState
import com.github.fschroffner.radiodroid3.player.PlayerViewModel
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.station.StationActions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FragmentPlayerSmall : Fragment() {

    enum class Role { HEADER, PLAYER }

    interface Callback {
        fun onToggle()
    }

    @Inject lateinit var trackHistoryRepository: TrackHistoryRepository

    private val viewModel: PlayerViewModel by activityViewModels()
    private var callback: Callback? = null
    private var role = Role.PLAYER

    private var _binding: LayoutPlayerSmallBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LayoutPlayerSmallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                checkStartupPlay()
            }
        })

        binding.buttonPlay.setOnClickListener {
            viewModel.playPause { station ->
                viewModel.showPlaySelection(requireContext(), station, requireActivity().supportFragmentManager)
            }
        }

        binding.buttonMore.setOnClickListener {
            val station = viewModel.uiState.value.station ?: viewModel.getCurrentOrLastStation() ?: return@setOnClickListener
            showPlayerMenu(station, viewModel.uiState.value.isFavorite)
        }

        requireView().setOnClickListener { callback?.onToggle() }

        setupStationIcon()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    fullUpdate(state)
                    if (state.isConnected) {
                        checkStartupPlay()
                    }
                }
            }
        }
    }

    fun setCallback(callback: Callback) { this.callback = callback }

    fun setRole(role: Role) {
        this.role = role
        fullUpdate(viewModel.uiState.value)
    }

    private fun checkStartupPlay() {
        val ctx = context ?: return
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx.applicationContext)
        val autoPlay = sharedPreferences.getBoolean("auto_play_on_startup", false)
        val autoOff = sharedPreferences.getBoolean("auto_off_on_startup", false)
        val timeout = try {
            sharedPreferences.getString("auto_off_timeout", "10")!!.toInt()
        } catch (e: Exception) { 10 }

        viewModel.tryPlayAtStart(autoPlay, autoOff, timeout) { station ->
            if (isAdded) {
                viewModel.showPlaySelection(requireContext(), station, requireActivity().supportFragmentManager)
            }
        }
    }

    private fun setupStationIcon() {
        val b = _binding ?: return
        val useCircularIcons = PreferenceManager.getDefaultSharedPreferences(requireContext().applicationContext).getBoolean("circular_icons", false)
        if (useCircularIcons) {
            b.playerRadioImage.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.black))
        }
        b.transparentCircle.visibility = if (useCircularIcons) View.VISIBLE else View.GONE
    }

    private fun fullUpdate(state: PlayerUiState) {
        val b = _binding ?: return
        if (state.isPlaying) {
            b.buttonPlay.setImageResource(R.drawable.ic_pause_circle)
            b.buttonPlay.contentDescription = resources.getString(R.string.detail_pause)
        } else {
            b.buttonPlay.setImageResource(R.drawable.ic_play_circle)
            b.buttonPlay.contentDescription = resources.getString(R.string.detail_play)
        }

        val station = state.station
        val stationName = station?.Name ?: ""
        b.textViewStationName.text = stationName

        val liveInfo = state.streamLiveInfo
        val streamTitle = liveInfo.title
        if (!TextUtils.isEmpty(streamTitle)) {
            b.textViewLiveInfo.visibility = View.VISIBLE
            b.textViewLiveInfo.text = streamTitle
            b.textViewStationName.gravity = Gravity.BOTTOM
        } else {
            b.textViewLiveInfo.visibility = View.GONE
            b.textViewStationName.gravity = Gravity.CENTER_VERTICAL
        }

        b.textViewLiveInfoBig.text = stationName

        if (!Utils.shouldLoadIcons(context)) {
            b.playerRadioImage.visibility = View.GONE
        } else if (station != null && station.hasIcon()) {
            b.playerRadioImage.visibility = View.VISIBLE
            Utils.loadStationIcon(b.playerRadioImage, station.IconUrl)
        } else {
            b.playerRadioImage.visibility = View.VISIBLE
            b.playerRadioImage.setImageResource(R.drawable.ic_launcher)
        }

        when (role) {
            Role.PLAYER -> {
                b.buttonPlay.visibility = View.VISIBLE
                b.buttonMore.visibility = View.GONE
                b.textViewStationName.visibility = View.VISIBLE
                b.textViewLiveInfoBig.visibility = View.GONE
            }
            Role.HEADER -> {
                b.buttonPlay.visibility = View.GONE
                b.buttonMore.visibility = View.VISIBLE
                b.textViewLiveInfo.visibility = View.GONE
                b.textViewStationName.visibility = View.GONE
                b.textViewLiveInfoBig.visibility = View.VISIBLE
            }
        }
    }

    private fun showPlayerMenu(currentStation: DataRadioStation, stationIsInFavourites: Boolean) {
        val anchor = _binding?.buttonMore ?: return
        val dropDownMenu = PopupMenu(context, anchor)
        dropDownMenu.menuInflater.inflate(R.menu.menu_player, dropDownMenu.menu)
        dropDownMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_homepage -> StationActions.showWebLinks(requireActivity(), currentStation)
                R.id.action_share -> StationActions.share(requireContext(), currentStation)
                R.id.action_delete_stream_history -> trackHistoryRepository.deleteHistory()
            }
            true
        }
        dropDownMenu.show()
    }
}
