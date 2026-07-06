package com.github.fschroffner.radiodroid3.interfaces

import com.github.fschroffner.radiodroid3.station.StationsFilter

interface IFragmentSearchable {
    fun Search(searchStyle: StationsFilter.SearchStyle, query: String)
}
