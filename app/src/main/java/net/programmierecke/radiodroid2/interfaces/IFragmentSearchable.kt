package net.programmierecke.radiodroid2.interfaces

import net.programmierecke.radiodroid2.station.StationsFilter

interface IFragmentSearchable {
    fun Search(searchStyle: StationsFilter.SearchStyle, query: String)
}
