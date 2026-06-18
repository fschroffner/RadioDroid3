package net.programmierecke.radiodroid2.station

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import net.programmierecke.radiodroid2.RadioDroidApp
import net.programmierecke.radiodroid2.Utils
import net.programmierecke.radiodroid2.utils.CustomFilter
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.net.URLEncoder

class StationsFilter(
    private val context: Context,
    private val filterType: FilterType,
    private val dataProvider: DataProvider
) : CustomFilter() {

    enum class FilterType {
        LOCAL,
        GLOBAL,
    }

    enum class SearchStatus {
        SUCCESS,
        ERROR
    }

    enum class SearchStyle {
        ByName,
        ByLanguageExact,
        ByCountryCodeExact,
        ByTagExact,
    }

    interface DataProvider {
        fun getOriginalStationList(): List<DataRadioStation>
        fun notifyFilteredStationsChanged(status: SearchStatus, filteredStations: List<DataRadioStation>)
    }

    private val FUZZY_SEARCH_THRESHOLD = 55

    private var lastRemoteQuery = ""
    private var filteredStationsList: List<DataRadioStation> = emptyList()
    private var lastRemoteSearchStatus = SearchStatus.SUCCESS

    private var searchStyle = SearchStyle.ByName

    private inner class WeightedStation(val station: DataRadioStation, val weight: Int)

    fun setSearchStyle(searchStyle: SearchStyle) {
        Log.d("FILTER", "Changed search style:$searchStyle")
        this.searchStyle = searchStyle
    }

    private fun searchGlobal(query: String): List<DataRadioStation> {
        Log.d("FILTER", "searchGlobal 1:$query")
        val radioDroidApp = context.applicationContext as RadioDroidApp
        val httpClient = radioDroidApp.httpClient

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val showBroken = sharedPref.getBoolean("show_broken", false)

        val p = hashMapOf(
            "order" to "clickcount",
            "reverse" to "true",
            "hidebroken" to "${!showBroken}"
        )

        return try {
            var queryEncoded = URLEncoder.encode(query, "utf-8").replace("+", "%20")

            val searchUrl = when (searchStyle) {
                SearchStyle.ByName -> "json/stations/byname/$queryEncoded"
                SearchStyle.ByCountryCodeExact -> "json/stations/bycountrycodeexact/$queryEncoded"
                SearchStyle.ByLanguageExact -> "json/stations/bylanguageexact/$queryEncoded"
                SearchStyle.ByTagExact -> "json/stations/bytagexact/$queryEncoded"
            }

            Log.d("FILTER", "searchGlobal 2:$query")
            val resultString = Utils.downloadFeedRelative(httpClient, radioDroidApp, searchUrl, false, p)
            if (resultString != null) {
                Log.d("FILTER", "searchGlobal 3a:$query")
                lastRemoteSearchStatus = SearchStatus.SUCCESS
                DataRadioStation.DecodeJson(resultString)
            } else {
                Log.d("FILTER", "searchGlobal 3b:$query")
                lastRemoteSearchStatus = SearchStatus.ERROR
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            lastRemoteSearchStatus = SearchStatus.ERROR
            emptyList()
        }
    }

    fun clearList() {
        Log.d("FILTER", "forced refetch")
        lastRemoteQuery = ""
    }

    override fun performFiltering(constraint: CharSequence): FilterResults {
        val query = constraint.toString().lowercase()
        Log.d("FILTER", "performFiltering() $query")

        if (searchStyle == SearchStyle.ByName && (query.isEmpty() || (query.length < 2 && filterType == FilterType.GLOBAL))) {
            Log.d("FILTER", "performFiltering() 2 $query")
            filteredStationsList = dataProvider.getOriginalStationList()
            lastRemoteQuery = ""
        } else {
            Log.d("FILTER", "performFiltering() 3 $query")
            val stationsToFilter: List<DataRadioStation>
            val needsFiltering: Boolean

            if (lastRemoteQuery.isNotEmpty() && query.startsWith(lastRemoteQuery) && lastRemoteSearchStatus != SearchStatus.ERROR) {
                Log.d("FILTER", "performFiltering() 3a $query lastRemoteQuery=$lastRemoteQuery")
                stationsToFilter = filteredStationsList
                needsFiltering = true
            } else {
                Log.d("FILTER", "performFiltering() 3b $query")
                when (filterType) {
                    FilterType.LOCAL -> {
                        stationsToFilter = dataProvider.getOriginalStationList()
                        needsFiltering = true
                    }
                    FilterType.GLOBAL -> {
                        stationsToFilter = searchGlobal(query)
                        needsFiltering = false
                        lastRemoteQuery = query
                    }
                }
            }

            if (needsFiltering) {
                Log.d("FILTER", "performFiltering() 4a $query")
                val filteredStations = stationsToFilter
                    .map { WeightedStation(it, FuzzySearch.partialRatio(query, it.Name.lowercase())) }
                    .filter { it.weight > FUZZY_SEARCH_THRESHOLD }
                    .map { WeightedStation(it.station, it.weight / 4) }
                    .sortedWith(compareByDescending<WeightedStation> { it.weight }.thenByDescending { it.station.ClickCount })

                filteredStationsList = filteredStations.map { it.station }
            } else {
                Log.d("FILTER", "performFiltering() 4b $query")
                filteredStationsList = stationsToFilter
            }
        }

        return FilterResults().apply { values = filteredStationsList }
    }

    @Suppress("UNCHECKED_CAST")
    override fun publishResults(constraint: CharSequence, results: FilterResults) {
        dataProvider.notifyFilteredStationsChanged(lastRemoteSearchStatus, results.values as List<DataRadioStation>)
    }
}
