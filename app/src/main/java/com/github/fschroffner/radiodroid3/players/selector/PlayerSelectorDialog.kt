package com.github.fschroffner.radiodroid3.players.selector

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.di.AppEntryPoint
import dagger.hilt.EntryPoints
import com.github.fschroffner.radiodroid3.Utils.parseIntWithDefault
import com.github.fschroffner.radiodroid3.players.mpd.MPDClient
import com.github.fschroffner.radiodroid3.players.mpd.MPDServerData
import com.github.fschroffner.radiodroid3.players.mpd.MPDServersRepository
import com.github.fschroffner.radiodroid3.station.DataRadioStation

class PlayerSelectorDialog : BottomSheetDialogFragment {

    private val mpdClient: MPDClient
    private val stationToPlay: DataRadioStation?

    private lateinit var recyclerViewServers: RecyclerView
    private lateinit var playerSelectorAdapter: PlayerSelectorAdapter
    private lateinit var serversRepository: MPDServersRepository
    private lateinit var btnEnableMPD: Button
    private lateinit var btnAddMPDServer: Button

    constructor(mpdClient: MPDClient) {
        this.mpdClient = mpdClient
        this.stationToPlay = null
    }

    constructor(mpdClient: MPDClient, stationToPlay: DataRadioStation?) {
        this.mpdClient = mpdClient
        this.stationToPlay = stationToPlay
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        retainInstance = true
        val view = inflater.inflate(R.layout.dialog_mpd_servers, container, false)


        serversRepository = EntryPoints.get(requireContext().applicationContext, AppEntryPoint::class.java).mpdClient().mpdServersRepository

        recyclerViewServers = view.findViewById(R.id.recyclerViewMPDServers)
        recyclerViewServers.layoutManager = GridLayoutManager(context, 2, RecyclerView.VERTICAL, false)

        playerSelectorAdapter = PlayerSelectorAdapter(requireContext(), stationToPlay)
        playerSelectorAdapter.setActionListener(object : PlayerSelectorAdapter.ActionListener {
            override fun editServer(mpdServerData: MPDServerData) { editOrAddServer(MPDServerData(mpdServerData)) }
            override fun removeServer(mpdServerData: MPDServerData) { serversRepository.removeServer(mpdServerData) }
        })
        recyclerViewServers.adapter = playerSelectorAdapter

        btnEnableMPD = view.findViewById(R.id.btnEnableMPD)
        btnAddMPDServer = view.findViewById(R.id.btnAddMPDServer)

        btnEnableMPD.setOnClickListener {
            val mpdEnabled = !mpdClient.isMpdEnabled
            mpdClient.setMPDEnabled(mpdEnabled)
            if (mpdEnabled) mpdClient.enableAutoUpdate() else mpdClient.disableAutoUpdate()
            updateEnableMpdButton()
        }

        btnAddMPDServer.setOnClickListener { editOrAddServer(null) }

        serversRepository.getAllServers().observe(this) { servers -> playerSelectorAdapter.setEntries(servers) }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val entryPoint = dagger.hilt.EntryPoints.get(requireContext().applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                entryPoint.mediaControllerRepository().playbackState.collect {
                    playerSelectorAdapter.notifyRadioDroidPlaybackStateChanged()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (mpdClient.isMpdEnabled) mpdClient.enableAutoUpdate()
        updateEnableMpdButton()
    }

    override fun onPause() {
        super.onPause()
        mpdClient.disableAutoUpdate()
    }

    private fun updateEnableMpdButton() {
        btnEnableMPD.setText(if (mpdClient.isMpdEnabled) R.string.action_disable_mpd else R.string.action_enable_mpd)
    }

    private fun editOrAddServer(server: MPDServerData?) {
        val serverView = layoutInflater.inflate(R.layout.layout_server_alert, null)
        val editName = serverView.findViewById<EditText>(R.id.mpd_server_name)
        val editHostname = serverView.findViewById<EditText>(R.id.mpd_server_hostname)
        val editPassword = serverView.findViewById<EditText>(R.id.mpd_server_password)
        val editPort = serverView.findViewById<EditText>(R.id.mpd_server_port)

        if (server != null) {
            editName.setText(server.name)
            editHostname.setText(server.hostname)
            editPort.setText(server.port.toString())
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(serverView)
            .setPositiveButton(R.string.alert_select_mpd_server_save, null)
            .setNeutralButton(R.string.alert_select_mpd_server_remove, null)
            .setTitle(R.string.alert_add_or_edit_mpd_server)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val serverName = editName.text.toString().trim()
                val hostname = editHostname.text.toString().trim()
                val password = editPassword.text.toString().trim()
                val port = parseIntWithDefault(editPort.text.toString().trim(), 0)
                if (serverName.isEmpty() || hostname.isEmpty() || port == 0) return@setOnClickListener
                if (server != null) {
                    server.name = serverName
                    server.hostname = hostname
                    server.port = port
                    server.password = password
                    serversRepository.updatePersistentData(server)
                } else {
                    serversRepository.addServer(MPDServerData(serverName, hostname, port, password))
                }
                mpdClient.launchQuickCheck()
                dialog.cancel()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (server != null) {
                    serversRepository.removeServer(server)
                    mpdClient.launchQuickCheck()
                }
                dialog.cancel()
            }
        }

        editName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        editName.requestFocus()
        dialog.show()
    }

    companion object {
        const val FRAGMENT_TAG = "mpd_servers_dialog_fragment"
    }
}
