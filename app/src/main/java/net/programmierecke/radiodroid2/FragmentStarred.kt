package net.programmierecke.radiodroid2

import android.content.Intent
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import net.programmierecke.radiodroid2.interfaces.IAdapterRefreshable
import net.programmierecke.radiodroid2.station.DataRadioStation
import net.programmierecke.radiodroid2.station.ItemAdapterIconOnlyStation
import net.programmierecke.radiodroid2.station.ItemAdapterStation
import net.programmierecke.radiodroid2.station.StationActions
import net.programmierecke.radiodroid2.station.StationsFilter
import java.util.Observable
import java.util.Observer

class FragmentStarred : Fragment(), IAdapterRefreshable, Observer {
    private val TAG = "FragmentStarred"

    private lateinit var rvStations: RecyclerView
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var task: AsyncTask<*, *, *>? = null
    private lateinit var favouriteManager: FavouriteManager

    private fun onStationClick(station: DataRadioStation) {
        val app = requireActivity().application as RadioDroidApp
        Utils.showPlaySelection(app, station, requireActivity().supportFragmentManager)
    }

    override fun RefreshListGui() {
        if (BuildConfig.DEBUG) Log.d(TAG, "refreshing the stations list.")
        val adapter = rvStations.adapter as? ItemAdapterStation ?: return
        if (BuildConfig.DEBUG) Log.d(TAG, "stations count:${favouriteManager.listStations.size}")
        adapter.updateList(this, favouriteManager.listStations)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val app = requireActivity().application as RadioDroidApp
        favouriteManager = app.favouriteManager
        favouriteManager.addObserver(this)

        val view = inflater.inflate(R.layout.fragment_stations, container, false)
        rvStations = view.findViewById(R.id.recyclerViewStations)

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
                StationActions.removeFromFavourites(requireContext(), view, station)
            }
            override fun onStationMoved(from: Int, to: Int) {
                favouriteManager.moveWithoutNotify(from, to)
            }
            override fun onStationMoveFinished() {
                requireView().post {
                    favouriteManager.Save()
                    favouriteManager.notifyObservers()
                }
            }
        })

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
        val listUuids = favouriteManager.listStations.map { it.StationUuid }
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
        for (current in favouriteManager.listStations) {
            if (listNew.none { it.StationUuid == current.StationUuid }) {
                Log.d(TAG, "Remove station: ${current.StationUuid} - ${current.Name}")
                toRemove.add(current.StationUuid)
                current.DeletedOnServer = true
            }
        }
        Log.d(TAG, "replace items")
        favouriteManager.replaceList(listNew)
        Log.d(TAG, "fin save")
        if (toRemove.isNotEmpty()) {
            Toast.makeText(context, resources.getString(R.string.notify_sync_list_deleted_entries, toRemove.size, favouriteManager.size()), Toast.LENGTH_LONG).show()
        }
    }

    private fun DownloadFinished() { swipeRefreshLayout?.isRefreshing = false }

    override fun onDestroyView() {
        super.onDestroyView()
        rvStations.adapter = null
        favouriteManager = (requireActivity().application as RadioDroidApp).favouriteManager
        favouriteManager.deleteObserver(this)
    }

    override fun update(o: Observable?, arg: Any?) { RefreshListGui() }
}
