package com.github.fschroffner.radiodroid3

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.fschroffner.radiodroid3.adapters.ItemAdapterCategory
import com.github.fschroffner.radiodroid3.data.DataCategory
import com.github.fschroffner.radiodroid3.databinding.FragmentStationsRemoteBinding
import com.github.fschroffner.radiodroid3.station.StationsFilter
import java.util.ArrayList
import java.util.Collections

class FragmentCategories : FragmentBase() {
    companion object {
        private const val TAG = "FragmentCategories"
    }

    private var _binding: FragmentStationsRemoteBinding? = null
    private val binding get() = _binding!!

    private var searchStyle = StationsFilter.SearchStyle.ByName
    private var singleUseFilter = false

    fun SetBaseSearchLink(searchStyle: StationsFilter.SearchStyle) {
        this.searchStyle = searchStyle
    }

    fun ClickOnItem(theData: DataCategory) {
        (requireActivity() as ActivityMain).Search(searchStyle, theData.Name)
    }

    override fun RefreshListGui() {
        val currentBinding = _binding ?: return
        if (BuildConfig.DEBUG) Log.d(TAG, "refreshing the categories list.")

        val ctx = context ?: return
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx)
        val showSingleUseTags = sharedPref.getBoolean("single_use_tags", false)

        val filteredCategoriesList = ArrayList<DataCategory>()
        val data = DataCategory.DecodeJson(urlResult)

        if (BuildConfig.DEBUG) Log.d(TAG, "categories count:${data.size}")
        val countryDict = CountryCodeDictionary.instance
        val flagsDict = CountryFlagsLoader.instance

        for (aData in data) {
            if (!singleUseFilter || showSingleUseTags || aData.UsedCount > 1) {
                if (searchStyle == StationsFilter.SearchStyle.ByCountryCodeExact) {
                    aData.Label = countryDict.getCountryByCode(aData.Name)
                    aData.Icon = flagsDict.getFlag(requireContext(), aData.Name)
                }
                filteredCategoriesList.add(aData)
            }
        }

        if (searchStyle == StationsFilter.SearchStyle.ByCountryCodeExact) {
            Collections.sort(filteredCategoriesList)
        }
        (currentBinding.recyclerViewStations.adapter as? ItemAdapterCategory)?.updateList(filteredCategoriesList)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStationsRemoteBinding.inflate(inflater, container, false)

        val adapterCategory = ItemAdapterCategory(R.layout.list_item_category)
        adapterCategory.categoryClickListener = object : ItemAdapterCategory.CategoryClickListener {
            override fun onCategoryClick(category: DataCategory) {
                ClickOnItem(category)
            }
        }

        val llm = LinearLayoutManager(context).apply { orientation = LinearLayoutManager.VERTICAL }
        binding.recyclerViewStations.adapter = adapterCategory
        binding.recyclerViewStations.layoutManager = llm

        binding.swiperefresh.setOnRefreshListener {
            if (BuildConfig.DEBUG) Log.d(TAG, "onRefresh called from SwipeRefreshLayout")
            DownloadUrl(true, false)
        }

        RefreshListGui()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun EnableSingleUseFilter(b: Boolean) {
        singleUseFilter = b
    }

    override fun DownloadFinished() {
        _binding?.swiperefresh?.isRefreshing = false
    }
}
