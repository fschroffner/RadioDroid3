package com.github.fschroffner.radiodroid3

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.fschroffner.radiodroid3.data.loading.LoadingStateHolder
import com.github.fschroffner.radiodroid3.adapters.ItemAdapterStatistics
import com.github.fschroffner.radiodroid3.data.DataStatistics
import com.github.fschroffner.radiodroid3.databinding.LayoutStatisticsBinding
import com.github.fschroffner.radiodroid3.di.AppEntryPoint
import dagger.hilt.EntryPoints
import com.github.fschroffner.radiodroid3.interfaces.IFragmentRefreshable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class FragmentServerInfo : Fragment(), IFragmentRefreshable {
    private var itemAdapterStatistics: ItemAdapterStatistics? = null
    private var _binding: LayoutStatisticsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LayoutStatisticsBinding.inflate(inflater, container, false)

        if (itemAdapterStatistics == null) {
            itemAdapterStatistics = ItemAdapterStatistics(requireActivity(), R.layout.list_item_statistic)
        }

        binding.listViewStatistics.adapter = itemAdapterStatistics

        download(false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun download(forceUpdate: Boolean) {
        val currentContext = context ?: return
        LoadingStateHolder.show()
        val httpClient = EntryPoints.get(currentContext.applicationContext, AppEntryPoint::class.java).okHttpClient()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                activity?.let { act ->
                    Utils.downloadFeedRelative(httpClient, act, "json/stats", forceUpdate, null)
                }
            }
            if (context != null) {
                LoadingStateHolder.hide()
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
    }

    override fun Refresh() { download(true) }
}
