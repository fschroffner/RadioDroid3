package net.programmierecke.radiodroid2

import android.content.Context
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import net.programmierecke.radiodroid2.interfaces.IFragmentRefreshable
import net.programmierecke.radiodroid2.interfaces.IFragmentSearchable
import net.programmierecke.radiodroid2.station.FragmentStations
import net.programmierecke.radiodroid2.station.StationsFilter

class FragmentTabs : Fragment(), IFragmentRefreshable, IFragmentSearchable {
    private val itsAdressWWWLocal = "json/stations/bycountryexact/internet?order=clickcount&reverse=true"
    private val itsAdressWWWTopClick = "json/stations/topclick/100"
    private val itsAdressWWWTopVote = "json/stations/topvote/100"
    private val itsAdressWWWChangedLately = "json/stations/lastchange/100"
    private val itsAdressWWWCurrentlyHeard = "json/stations/lastclick/100"
    private val itsAdressWWWTags = "json/tags"
    private val itsAdressWWWCountries = "json/countrycodes"
    private val itsAdressWWWLanguages = "json/languages"

    private var queuedSearchQuery: String? = null
    private var queuedSearchStyle: StationsFilter.SearchStyle? = null

    private val fragments = arrayOfNulls<Fragment>(9)
    private val addresses = arrayOf(
        itsAdressWWWLocal,
        itsAdressWWWTopClick,
        itsAdressWWWTopVote,
        itsAdressWWWChangedLately,
        itsAdressWWWCurrentlyHeard,
        itsAdressWWWTags,
        itsAdressWWWCountries,
        itsAdressWWWLanguages,
        ""
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val x = inflater.inflate(R.layout.layout_tabs, null)
        val tabLayout = requireActivity().findViewById<TabLayout>(R.id.tabs)
        viewPager = x.findViewById(R.id.viewpager)

        setupViewPager(viewPager!!)

        if (queuedSearchQuery != null) {
            Log.d("TABS", "do queued search by name:$queuedSearchQuery")
            Search(queuedSearchStyle!!, queuedSearchQuery!!)
            queuedSearchQuery = null
            queuedSearchStyle = StationsFilter.SearchStyle.ByName
        }

        tabLayout.post {
            if (context != null) tabLayout.setupWithViewPager(viewPager)
        }

        return x
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<TabLayout>(R.id.tabs).visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        requireActivity().findViewById<TabLayout>(R.id.tabs).visibility = View.GONE
    }

    private fun getCountryCode(): String? {
        val ctx = context ?: return null
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        var countryCode = tm.networkCountryIso
        Log.d("MAIN", "Network country code: '$countryCode'")
        if (countryCode != null && countryCode.length == 2) return countryCode
        countryCode = tm.simCountryIso
        Log.d("MAIN", "Sim country code: '$countryCode'")
        if (countryCode != null && countryCode.length == 2) return countryCode
        countryCode = ctx.resources.configuration.locale.country
        addresses[IDX_LOCAL] = "json/stations/bycountrycodeexact/?order=clickcount&reverse=true"
        Log.d("MAIN", "Locale: '$countryCode'")
        if (countryCode != null && countryCode.length == 2) return countryCode
        return null
    }

    private fun setupViewPager(viewPager: ViewPager) {
        val countryCode = getCountryCode()
        if (countryCode != null) {
            addresses[IDX_LOCAL] = "json/stations/bycountrycodeexact/$countryCode?order=clickcount&reverse=true"
        }

        fragments[IDX_LOCAL] = FragmentStations()
        fragments[IDX_TOP_CLICK] = FragmentStations()
        fragments[IDX_TOP_VOTE] = FragmentStations()
        fragments[IDX_CHANGED_LATELY] = FragmentStations()
        fragments[IDX_CURRENTLY_HEARD] = FragmentStations()
        fragments[IDX_TAGS] = FragmentCategories()
        fragments[IDX_COUNTRIES] = FragmentCategories()
        fragments[IDX_LANGUAGES] = FragmentCategories()
        fragments[IDX_SEARCH] = FragmentStations()

        for (i in fragments.indices) {
            val bundle = Bundle().apply {
                putString("url", addresses[i])
                if (i == IDX_SEARCH) putBoolean(FragmentStations.KEY_SEARCH_ENABLED, true)
            }
            fragments[i]!!.arguments = bundle
        }

        (fragments[IDX_TAGS] as FragmentCategories).EnableSingleUseFilter(true)
        (fragments[IDX_TAGS] as FragmentCategories).SetBaseSearchLink(StationsFilter.SearchStyle.ByTagExact)
        (fragments[IDX_COUNTRIES] as FragmentCategories).SetBaseSearchLink(StationsFilter.SearchStyle.ByCountryCodeExact)
        (fragments[IDX_LANGUAGES] as FragmentCategories).SetBaseSearchLink(StationsFilter.SearchStyle.ByLanguageExact)

        val adapter = ViewPagerAdapter(childFragmentManager)
        if (countryCode != null) adapter.addFragment(fragments[IDX_LOCAL]!!, R.string.action_local)
        adapter.addFragment(fragments[IDX_TOP_CLICK]!!, R.string.action_top_click)
        adapter.addFragment(fragments[IDX_TOP_VOTE]!!, R.string.action_top_vote)
        adapter.addFragment(fragments[IDX_CHANGED_LATELY]!!, R.string.action_changed_lately)
        adapter.addFragment(fragments[IDX_CURRENTLY_HEARD]!!, R.string.action_currently_playing)
        adapter.addFragment(fragments[IDX_TAGS]!!, R.string.action_tags)
        adapter.addFragment(fragments[IDX_COUNTRIES]!!, R.string.action_countries)
        adapter.addFragment(fragments[IDX_LANGUAGES]!!, R.string.action_languages)
        adapter.addFragment(fragments[IDX_SEARCH]!!, R.string.action_search)
        viewPager.adapter = adapter
    }

    override fun Search(searchStyle: StationsFilter.SearchStyle, query: String) {
        Log.d("TABS", "Search = $query searchStyle=$searchStyle")
        if (viewPager != null) {
            Log.d("TABS", "a Search = $query")
            viewPager!!.setCurrentItem(IDX_SEARCH, false)
            (fragments[IDX_SEARCH] as IFragmentSearchable).Search(searchStyle, query)
        } else {
            Log.d("TABS", "b Search = $query")
            queuedSearchQuery = query
            queuedSearchStyle = searchStyle
        }
    }

    override fun Refresh() {
        val fragment = fragments[viewPager!!.currentItem]
        if (fragment is FragmentBase) fragment.DownloadUrl(true)
    }

    @Suppress("DEPRECATION")
    inner class ViewPagerAdapter(manager: FragmentManager) : FragmentPagerAdapter(manager) {
        private val mFragmentList = mutableListOf<Fragment>()
        private val mFragmentTitleList = mutableListOf<Int>()

        override fun getItem(position: Int) = mFragmentList[position]
        override fun getCount() = mFragmentList.size

        fun addFragment(fragment: Fragment, title: Int) {
            mFragmentList.add(fragment)
            mFragmentTitleList.add(title)
        }

        override fun getPageTitle(position: Int): CharSequence = resources.getString(mFragmentTitleList[position])
    }

    companion object {
        private const val IDX_LOCAL = 0
        private const val IDX_TOP_CLICK = 1
        private const val IDX_TOP_VOTE = 2
        private const val IDX_CHANGED_LATELY = 3
        private const val IDX_CURRENTLY_HEARD = 4
        private const val IDX_TAGS = 5
        private const val IDX_COUNTRIES = 6
        private const val IDX_LANGUAGES = 7
        private const val IDX_SEARCH = 8

        @JvmField
        var viewPager: ViewPager? = null
    }
}
