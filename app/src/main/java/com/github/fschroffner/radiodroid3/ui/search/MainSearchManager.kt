package com.github.fschroffner.radiodroid3.ui.search

import android.content.Context
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.fragment.app.FragmentManager
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.interfaces.IFragmentSearchable
import com.github.fschroffner.radiodroid3.station.StationsFilter
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout

/**
 * Encapsulates search interactions, query dispatching to searchable fragments,
 * and UI visibility toggling during search focus.
 */
class MainSearchManager(
    private val context: Context,
    private val fragmentManager: FragmentManager,
    private val tabsView: TabLayout,
    private val bottomNavigationView: BottomNavigationView
) : SearchView.OnQueryTextListener {

    private val tag = "MainSearchManager"
    var searchView: SearchView? = null
        private set

    fun setupSearchMenuItem(menuItemSearch: MenuItem): SearchView {
        val sv = MenuItemCompat.getActionView(menuItemSearch) as SearchView
        searchView = sv
        sv.setOnQueryTextListener(this)
        sv.setOnQueryTextFocusChangeListener(object : View.OnFocusChangeListener {
            private var prevTabsVisibility = View.GONE

            override fun onFocusChange(v: View, hasFocus: Boolean) {
                if (Utils.bottomNavigationEnabled(context)) {
                    bottomNavigationView.visibility = if (hasFocus) View.GONE else View.VISIBLE
                }
                if (hasFocus) {
                    prevTabsVisibility = tabsView.visibility
                    tabsView.visibility = View.GONE
                } else {
                    tabsView.visibility = prevTabsVisibility
                }
            }
        })
        return sv
    }

    fun clearFocus() {
        searchView?.clearFocus()
    }

    override fun onQueryTextSubmit(query: String): Boolean = true

    override fun onQueryTextChange(newText: String): Boolean {
        searchStations(newText)
        return true
    }

    fun searchStations(query: String) {
        Log.d(tag, "SearchStations() $query")
        val currentFragment = fragmentManager.fragments.lastOrNull()
        if (currentFragment is IFragmentSearchable) {
            currentFragment.Search(StationsFilter.SearchStyle.ByName, query)
        }
    }
}
