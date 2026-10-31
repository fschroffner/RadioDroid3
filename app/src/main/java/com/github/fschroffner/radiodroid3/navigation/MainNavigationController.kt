package com.github.fschroffner.radiodroid3.navigation

import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.github.fschroffner.radiodroid3.data.loading.LoadingStateHolder
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.FavouriteManager
import com.github.fschroffner.radiodroid3.FragmentHistory
import com.github.fschroffner.radiodroid3.FragmentSettings
import com.github.fschroffner.radiodroid3.FragmentStarred
import com.github.fschroffner.radiodroid3.FragmentTabs
import com.github.fschroffner.radiodroid3.HistoryManager
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.station.StationsFilter
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import java.util.Date

/**
 * Handles application navigation, destination selection, and backstack coordination.
 * Decouples navigation logic and fragment transactions from ActivityMain.
 */
class MainNavigationController(
    private val activity: AppCompatActivity,
    private val fragmentManager: FragmentManager,
    private val drawerLayout: DrawerLayout,
    private val navigationView: NavigationView,
    private val bottomNavigationView: BottomNavigationView,
    private val appBarLayout: AppBarLayout,
    private val sharedPref: SharedPreferences,
    private val historyManager: HistoryManager,
    private val favouriteManager: FavouriteManager,
    private val onBottomSheetCollapseRequested: () -> Boolean,
    private val onServiceShutdownRequested: () -> Unit
) {

    var selectedMenuItem: Int = -1
        private set

    private var lastExitTry: Date? = null

    init {
        selectedMenuItem = sharedPref.getInt("last_selectedMenuItem", -1)
    }

    fun setupStartUpFragment(instanceStateWasSaved: Boolean) {
        if (instanceStateWasSaved) {
            activity.invalidateOptionsMenu()
            syncMenuSelection()
            return
        }

        val startupAction = sharedPref.getString(
            "startup_action",
            activity.resources.getString(R.string.startup_show_history)
        ) ?: activity.resources.getString(R.string.startup_show_history)

        val historyEmpty = historyManager.isEmpty()
        val favoritesEmpty = favouriteManager.isEmpty()

        if (startupAction == activity.resources.getString(R.string.startup_show_history) && historyEmpty) {
            selectMenuItem(R.id.nav_item_stations)
            return
        }
        if (startupAction == activity.resources.getString(R.string.startup_show_favorites) && favoritesEmpty) {
            selectMenuItem(R.id.nav_item_stations)
            return
        }

        when {
            startupAction == activity.resources.getString(R.string.startup_show_history) -> selectMenuItem(R.id.nav_item_history)
            startupAction == activity.resources.getString(R.string.startup_show_favorites) -> selectMenuItem(R.id.nav_item_starred)
            startupAction == activity.resources.getString(R.string.startup_show_all_stations) || selectedMenuItem < 0 -> selectMenuItem(R.id.nav_item_stations)
            else -> selectMenuItem(selectedMenuItem)
        }
    }

    fun selectMenuItem(itemId: Int) {
        val item = if (Utils.bottomNavigationEnabled(activity)) {
            bottomNavigationView.menu.findItem(itemId)
        } else {
            navigationView.menu.findItem(itemId)
        }

        if (item != null) {
            onNavigationItemSelected(itemId)
        } else {
            selectedMenuItem = R.id.nav_item_stations
            navigateToSelectedMenuItem()
        }
    }

    fun onNavigationItemSelected(itemId: Int): Boolean {
        selectedMenuItem = itemId
        return navigateToSelectedMenuItem()
    }

    fun navigateToSelectedMenuItem(): Boolean {
        onBottomSheetCollapseRequested()

        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawers()
        }

        val fragment: Fragment = when (selectedMenuItem) {
            R.id.nav_item_stations -> FragmentTabs()
            R.id.nav_item_starred -> FragmentStarred()
            R.id.nav_item_history -> FragmentHistory()
            R.id.nav_item_settings -> FragmentSettings()
            else -> return false
        }

        fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val fragmentTransaction = fragmentManager.beginTransaction()
        val backStackTag = selectedMenuItem.toString()

        if (Utils.bottomNavigationEnabled(activity)) {
            fragmentTransaction.replace(R.id.containerView, fragment).commit()
        } else {
            fragmentTransaction.replace(R.id.containerView, fragment).addToBackStack(backStackTag).commit()
        }

        LoadingStateHolder.hide()
        activity.invalidateOptionsMenu()
        syncMenuSelection()
        appBarLayout.setExpanded(true)

        return false
    }

    fun syncMenuSelection() {
        bottomNavigationView.menu.findItem(selectedMenuItem)?.isChecked = true
        navigationView.menu.findItem(selectedMenuItem)?.isChecked = true
    }

    fun handleBackPressed(): Boolean {
        if (onBottomSheetCollapseRequested()) {
            return true
        }

        val backStackCount = fragmentManager.backStackEntryCount

        if (backStackCount > 0) {
            val backStackEntry = fragmentManager.getBackStackEntryAt(backStackCount - 1)
            if (backStackEntry.name == "SearchPreferenceFragment") {
                fragmentManager.popBackStack()
                return true
            }
            val parsedId = backStackEntry.name?.toIntOrNull()
            if (parsedId == ActivityMain.FRAGMENT_FROM_BACKSTACK) {
                fragmentManager.popBackStack()
                activity.invalidateOptionsMenu()
                return true
            }
        }

        if (Utils.bottomNavigationEnabled(activity)) {
            val now = Date().time
            if (lastExitTry != null && now < lastExitTry!!.time + 3 * 1000) {
                onServiceShutdownRequested()
                activity.finish()
                return true
            } else {
                Toast.makeText(activity, R.string.alert_press_back_to_exit, Toast.LENGTH_SHORT).show()
                lastExitTry = Date()
                return true
            }
        }

        if (backStackCount > 1) {
            val backStackEntry = fragmentManager.getBackStackEntryAt(backStackCount - 2)
            selectedMenuItem = backStackEntry.name?.toIntOrNull() ?: R.id.nav_item_stations

            if (!Utils.bottomNavigationEnabled(activity)) {
                navigationView.setCheckedItem(selectedMenuItem)
            }
            activity.invalidateOptionsMenu()
            fragmentManager.popBackStack()
            return true
        }

        return false
    }

    fun navigateToSearch(searchStyle: StationsFilter.SearchStyle, query: String) {
        val currentFragment = fragmentManager.fragments.lastOrNull()
        if (currentFragment is FragmentTabs) {
            currentFragment.Search(searchStyle, query)
        } else {
            val backStackTag = R.id.nav_item_stations.toString()
            val tabsFragment = FragmentTabs()
            val fragmentTransaction = fragmentManager.beginTransaction()

            if (Utils.bottomNavigationEnabled(activity)) {
                fragmentTransaction.replace(R.id.containerView, tabsFragment).commit()
                bottomNavigationView.menu.findItem(R.id.nav_item_stations)?.isChecked = true
            } else {
                fragmentTransaction.replace(R.id.containerView, tabsFragment).addToBackStack(backStackTag).commit()
                navigationView.menu.findItem(R.id.nav_item_stations)?.isChecked = true
            }

            tabsFragment.Search(searchStyle, query)
            selectedMenuItem = R.id.nav_item_stations
            activity.invalidateOptionsMenu()
        }
    }

    fun saveSelectedMenuItem() {
        sharedPref.edit().putInt("last_selectedMenuItem", selectedMenuItem).apply()
    }
}
