package net.programmierecke.radiodroid2.players.selector

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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.RadioDroidApp
import net.programmierecke.radiodroid2.Utils.parseIntWithDefault
import net.programmierecke.radiodroid2.players.mpd.MPDClient
import net.programmierecke.radiodroid2.players.mpd.MPDServerData
import net.programmierecke.radiodroid2.players.mpd.MPDServersRepository
import net.programmierecke.radiodroid2.service.PlayerService
import net.programmierecke.radiodroid2.station.DataRadioStation

class PlayerSelectorDialog : BottomSheetDialogFragment {

    private val mpdClient: MPDClient
    private val stationToPlay: DataRadioStation?

    private lateinit var updateUIReceiver: BroadcastReceiver
    private lateinit var recyclerViewServers: RecyclerView
    private lateinit var playerSelectorAdapter: PlayerSelectorAdapter
    private lateinit var serversRepository: MPDServersRepository
    private lateinit var btnEnableMPD: Button
    private lateinit var btnAddMPDServer: Button

    constructor(mpdClient: MPDClient) {
        this.mpdClient = mpdClient
        this.stationToPlay = null
    }

    constructor(mpdClient: MPDClient, stationToPlay: DataRadioStation) {
        this.mpdClient = mpdClient
        this.stationToPlay = stationToPlay
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        retainInstance = true
        val view = inflater.inflate(R.layout.dialog_mpd_servers, container, false)

        val radioDroidApp = requireActivity().application as RadioDroidApp
        serversRepository = radioDroidApp.mpdClient.mpdServersRepository

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
            val mpdEnabled = !mpdClient.isMpdEnabled()
            mpdClient.setMPDEnabled(mpdEnabled)
            if (mpdEnabled) mpdClient.enableAutoUpdate() else mpdClient.disableAutoUpdate()
            updateEnableMpdButton()
        }

        btnAddMPDServer.setOnClickListener { editOrAddServer(null) }

        serversRepository.getAllServers().observe(this) { servers -> playerSelectorAdapter.setEntries(servers) }

        updateUIReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (PlayerService.PLAYER_SERVICE_STATE_CHANGE == intent.action) {
                    playerSelectorAdapter.notifyRadioDroidPlaybackStateChanged()
                }
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        if (mpdClient.isMpdEnabled()) mpdClient.enableAutoUpdate()
        val filter = IntentFilter(PlayerService.PLAYER_SERVICE_STATE_CHANGE)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(updateUIReceiver, filter)
        updateEnableMpdButton()
    }

    override fun onPause() {
        super.onPause()
        mpdClient.disableAutoUpdate()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(updateUIReceiver)
    }

    private fun updateEnableMpdButton() {
        btnEnableMPD.setText(if (mpdClient.isMpdEnabled()) R.string.action_disable_mpd else R.string.action_enable_mpd)
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
