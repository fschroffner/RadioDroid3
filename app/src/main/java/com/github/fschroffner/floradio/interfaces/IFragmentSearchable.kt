package com.github.fschroffner.floradio.interfaces

import com.github.fschroffner.floradio.station.StationsFilter

interface IFragmentSearchable {
    fun Search(searchStyle: StationsFilter.SearchStyle, query: String)
}
