package net.programmierecke.radiodroid2

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import net.programmierecke.radiodroid2.adapters.ItemAdapterCategory
import net.programmierecke.radiodroid2.data.DataCategory
import net.programmierecke.radiodroid2.station.StationsFilter
import java.util.Collections

class FragmentCategories : FragmentBase() {
    companion object {
        private const val TAG = "FragmentCategories"
    }

    private var rvCategories: androidx.recyclerview.widget.RecyclerView? = null
    private var searchStyle = StationsFilter.SearchStyle.ByName
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var singleUseFilter = false

    fun SetBaseSearchLink(searchStyle: StationsFilter.SearchStyle) {
        this.searchStyle = searchStyle
    }

    fun ClickOnItem(theData: DataCategory) {
        (requireActivity() as ActivityMain).Search(searchStyle, theData.Name)
    }

    override fun RefreshListGui() {
        val rv = rvCategories ?: return
        if (BuildConfig.DEBUG) Log.d(TAG, "refreshing the categories list.")

        val ctx = context ?: return
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx)
        val showSingleUseTags = sharedPref.getBoolean("single_use_tags", false)

        val filteredCategoriesList = ArrayList<DataCategory>()
        val data = DataCategory.DecodeJson(urlResult)

        if (BuildConfig.DEBUG) Log.d(TAG, "categories count:${data.size}")
        val countryDict = CountryCodeDictionary.getInstance()
        val flagsDict = CountryFlagsLoader.getInstance()

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
        (rv.adapter as ItemAdapterCategory).updateList(filteredCategoriesList)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val adapterCategory = ItemAdapterCategory(R.layout.list_item_category)
        adapterCategory.setCategoryClickListener(object : ItemAdapterCategory.CategoryClickListener {
            override fun onCategoryClick(category: DataCategory) {
                ClickOnItem(category)
            }
        })

        val view = inflater.inflate(R.layout.fragment_stations_remote, container, false)

        val llm = LinearLayoutManager(context).apply { orientation = LinearLayoutManager.VERTICAL }
        rvCategories = view.findViewById(R.id.recyclerViewStations)
        rvCategories!!.adapter = adapterCategory
        rvCategories!!.layoutManager = llm

        swipeRefreshLayout = view.findViewById(R.id.swiperefresh)
        swipeRefreshLayout?.setOnRefreshListener {
            if (BuildConfig.DEBUG) Log.d(TAG, "onRefresh called from SwipeRefreshLayout")
            DownloadUrl(true, false)
        }

        RefreshListGui()
        return view
    }

    fun EnableSingleUseFilter(b: Boolean) {
        singleUseFilter = b
    }

    override fun DownloadFinished() {
        swipeRefreshLayout?.isRefreshing = false
    }
}
