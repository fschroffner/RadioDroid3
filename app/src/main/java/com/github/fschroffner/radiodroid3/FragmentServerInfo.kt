package com.github.fschroffner.radiodroid3

import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.fschroffner.radiodroid3.adapters.ItemAdapterStatistics
import com.github.fschroffner.radiodroid3.data.DataStatistics
import com.github.fschroffner.radiodroid3.interfaces.IFragmentRefreshable

class FragmentServerInfo : Fragment(), IFragmentRefreshable {
    private var itemAdapterStatistics: ItemAdapterStatistics? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.layout_statistics, null)

        if (itemAdapterStatistics == null) {
            itemAdapterStatistics = ItemAdapterStatistics(requireActivity(), R.layout.list_item_statistic)
        }

        view.findViewById<ListView>(R.id.listViewStatistics).adapter = itemAdapterStatistics

        download(false)
        return view
    }

    @Suppress("DEPRECATION")
    private fun download(forceUpdate: Boolean) {
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))
        val httpClient = (requireActivity().application as RadioDroidApp).httpClient

        object : AsyncTask<Void, Void, String?>() {
            override fun doInBackground(vararg params: Void?) =
                Utils.downloadFeedRelative(httpClient, requireActivity(), "json/stats", forceUpdate, null)

            override fun onPostExecute(result: String?) {
                if (context != null) {
                    LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))
                }
                if (result != null) {
                    itemAdapterStatistics?.clear()
                    DataStatistics.DecodeJson(result).forEach { itemAdapterStatistics?.add(it) }
                } else {
                    try {
                        Toast.makeText(context, resources.getText(R.string.error_list_update), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("ERR", e.toString())
                    }
                }
            }
        }.execute()
    }

    override fun Refresh() { download(true) }
}
