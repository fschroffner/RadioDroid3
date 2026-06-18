package net.programmierecke.radiodroid2

import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import net.programmierecke.radiodroid2.interfaces.IAdapterRefreshable
import net.programmierecke.radiodroid2.station.DataRadioStation
import net.programmierecke.radiodroid2.station.ItemAdapterStation
import net.programmierecke.radiodroid2.station.StationsFilter

class FragmentHistory : Fragment(), IAdapterRefreshable {
    private val TAG = "FragmentHistory"

    private lateinit var rvStations: RecyclerView
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var task: AsyncTask<*, *, *>? = null
    private lateinit var historyManager: HistoryManager

    private fun onStationClick(station: DataRadioStation) {
        val app = requireActivity().application as RadioDroidApp
        Utils.showPlaySelection(app, station, requireActivity().supportFragmentManager)
        RefreshListGui()
        rvStations.smoothScrollToPosition(0)
    }

    override fun RefreshListGui() {
        if (BuildConfig.DEBUG) Log.d(TAG, "refreshing the stations list.")
        val adapter = rvStations.adapter as? ItemAdapterStation ?: return
        if (BuildConfig.DEBUG) Log.d(TAG, "stations count:${historyManager.listStations.size}")
        adapter.updateList(null, historyManager.listStations)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val app = requireActivity().application as RadioDroidApp
        historyManager = app.historyManager

        val adapter = ItemAdapterStation(requireActivity(), R.layout.list_item_station, StationsFilter.FilterType.LOCAL)
        adapter.setStationActionsListener(object : ItemAdapterStation.StationActionsListener {
            override fun onStationClick(station: DataRadioStation, pos: Int) {
                this@FragmentHistory.onStationClick(station)
            }
            override fun onStationSwiped(station: DataRadioStation) {
                val removedIdx = historyManager.remove(station.StationUuid)
                RefreshListGui()
                val snackbar = Snackbar.make(rvStations, R.string.notify_station_removed_from_list, 6000)
                snackbar.anchorView = requireView().rootView.findViewById(R.id.bottom_sheet)
                snackbar.setAction(R.string.action_station_removed_from_list_undo) {
                    historyManager.restore(station, removedIdx)
                    RefreshListGui()
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
            RefreshDownloadList()
        }

        RefreshListGui()
        return view
    }

    @Suppress("DEPRECATION")
    private fun RefreshDownloadList() {
        val httpClient = (requireActivity().application as RadioDroidApp).httpClient
        val listUuids = historyManager.listStations.map { it.StationUuid }
        Log.d(TAG, "Search for items: ${listUuids.size}")

        task = object : AsyncTask<Void, Void, List<DataRadioStation>?>() {
            override fun doInBackground(vararg p: Void?) = Utils.getStationsByUuid(httpClient, requireActivity(), listUuids)
            override fun onPostExecute(result: List<DataRadioStation>?) {
                DownloadFinished()
                if (context != null)
                    LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))
                if (result != null) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Found items: ${result.size}")
                    SyncList(result)
                    RefreshListGui()
                } else {
                    try { Toast.makeText(context, resources.getText(R.string.error_list_update), Toast.LENGTH_SHORT).show() }
                    catch (e: Exception) { Log.e("ERR", e.toString()) }
                }
            }
        }.execute()
    }

    private fun SyncList(listNew: List<DataRadioStation>) {
        val toRemove = mutableListOf<String>()
        for (current in historyManager.listStations) {
            if (listNew.none { it.StationUuid == current.StationUuid }) {
                Log.d(TAG, "Remove station: ${current.StationUuid} - ${current.Name}")
                toRemove.add(current.StationUuid)
                current.DeletedOnServer = true
            }
        }
        Log.d(TAG, "replace items")
        historyManager.replaceList(listNew)
        Log.d(TAG, "fin save")
        if (toRemove.isNotEmpty()) {
            Toast.makeText(context, resources.getString(R.string.notify_sync_list_deleted_entries, toRemove.size, historyManager.size()), Toast.LENGTH_LONG).show()
        }
    }

    private fun DownloadFinished() { swipeRefreshLayout?.isRefreshing = false }

    override fun onDestroyView() {
        super.onDestroyView()
        rvStations.adapter = null
    }
}
