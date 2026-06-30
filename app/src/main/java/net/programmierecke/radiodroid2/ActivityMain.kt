package net.programmierecke.radiodroid2

import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.GravityCompat
import androidx.core.view.MenuItemCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.mikepenz.iconics.Iconics
import com.rustamg.filedialogs.FileDialog
import com.rustamg.filedialogs.OpenFileDialog
import com.rustamg.filedialogs.SaveFileDialog
import net.programmierecke.radiodroid2.alarm.FragmentAlarm
import net.programmierecke.radiodroid2.alarm.TimePickerFragment
import net.programmierecke.radiodroid2.cast.CastAwareActivity
import net.programmierecke.radiodroid2.interfaces.IFragmentSearchable
import net.programmierecke.radiodroid2.players.PlayStationTask
import net.programmierecke.radiodroid2.players.mpd.MPDClient
import net.programmierecke.radiodroid2.players.mpd.MPDServersRepository
import net.programmierecke.radiodroid2.players.selector.PlayerType
import net.programmierecke.radiodroid2.service.MediaSessionCallback
import net.programmierecke.radiodroid2.service.PlayerService
import net.programmierecke.radiodroid2.service.PlayerServiceUtil
import net.programmierecke.radiodroid2.station.DataRadioStation
import net.programmierecke.radiodroid2.station.StationsFilter
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.Date

class ActivityMain : AppCompatActivity(), SearchView.OnQueryTextListener,
    NavigationView.OnNavigationItemSelectedListener,
    BottomNavigationView.OnNavigationItemSelectedListener,
    FileDialog.OnFileSelectedListener,
    TimePickerDialog.OnTimeSetListener,
    SearchPreferenceResultListener,
    CastAwareActivity {

    companion object {
        const val EXTRA_SEARCH_TAG = "search_tag"
        const val LAUNCH_EQUALIZER_REQUEST = 1
        const val MAX_DYNAMIC_LAUNCHER_SHORTCUTS = 4
        const val FRAGMENT_FROM_BACKSTACK = 777
        const val ACTION_SHOW_LOADING = "net.programmierecke.radiodroid2.show_loading"
        const val ACTION_HIDE_LOADING = "net.programmierecke.radiodroid2.hide_loading"
        private const val TAG = "RadioDroid"
        const val PERM_REQ_STORAGE_FAV_SAVE = 1
        const val PERM_REQ_STORAGE_FAV_LOAD = 2
        private const val ACTION_SAVE_FILE = 1
        private const val ACTION_LOAD_FILE = 2
    }

    private val TAG_SEARCH_URL = "json/stations/bytagexact"
    private val SAVE_LAST_MENU_ITEM = "LAST_MENU_ITEM"

    private var mSearchView: SearchView? = null
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var tabsView: TabLayout
    lateinit var mDrawerLayout: DrawerLayout
    lateinit var mNavigationView: NavigationView
    lateinit var mBottomNavigationView: BottomNavigationView
    lateinit var mFragmentManager: FragmentManager

    private lateinit var playerBottomSheet: BottomSheetBehavior<View>
    private lateinit var smallPlayerFragment: FragmentPlayerSmall
    private lateinit var fullPlayerFragment: FragmentPlayerFull

    var broadcastReceiver: BroadcastReceiver? = null

    var menuItemSearch: MenuItem? = null
    var menuItemDelete: MenuItem? = null
    var menuItemSleepTimer: MenuItem? = null
    var menuItemSave: MenuItem? = null
    var menuItemLoad: MenuItem? = null
    var menuItemIconsView: MenuItem? = null
    var menuItemListView: MenuItem? = null
    var menuItemAddAlarm: MenuItem? = null
    var menuItemMpd: MenuItem? = null

    private lateinit var sharedPref: SharedPreferences
    private var selectedMenuItem = -1
    private var instanceStateWasSaved = false
    private var lastExitTry: Date? = null
    private var meteredConnectionAlertDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Iconics.init(this)
        super.onCreate(savedInstanceState)

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)

        setTheme(Utils.getThemeResId(this))
        setContentView(R.layout.layout_main)

        Log.d(TAG, "FilesDir: ${filesDir.absolutePath}")
        Log.d(TAG, "CacheDir: ${cacheDir.absolutePath}")
        try {
            val dir = File(filesDir.absolutePath)
            if (dir.isDirectory) {
                val children = dir.list()
                for (aChildren in children) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "delete file:$aChildren")
                    try { File(dir, aChildren).delete() } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {}

        val myToolbar = findViewById<Toolbar>(R.id.my_awesome_toolbar)
        setSupportActionBar(myToolbar)

        PlayerServiceUtil.startService(applicationContext)

        selectedMenuItem = sharedPref.getInt("last_selectedMenuItem", -1)
        instanceStateWasSaved = savedInstanceState != null
        mFragmentManager = supportFragmentManager

        appBarLayout = findViewById(R.id.app_bar_layout)
        tabsView = findViewById(R.id.tabs)
        mDrawerLayout = findViewById(R.id.drawerLayout)
        mNavigationView = findViewById(R.id.my_navigation_view)
        mBottomNavigationView = findViewById(R.id.bottom_navigation)

        if (Utils.bottomNavigationEnabled(this)) {
            mBottomNavigationView.setOnNavigationItemSelectedListener(this)
            mNavigationView.visibility = View.GONE
            mNavigationView.layoutParams.width = 0
        } else {
            mNavigationView.setNavigationItemSelectedListener(this)
            mBottomNavigationView.visibility = View.GONE

            val mDrawerToggle = ActionBarDrawerToggle(this, mDrawerLayout, R.string.app_name, R.string.app_name)
            mDrawerLayout.addDrawerListener(mDrawerToggle)
            mDrawerToggle.syncState()

            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.setHomeButtonEnabled(true)
        }

        var foundSmall = mFragmentManager.findFragmentById(R.id.fragment_player_small) as? FragmentPlayerSmall
        var foundFull = mFragmentManager.findFragmentById(R.id.fragment_player_full) as? FragmentPlayerFull

        if (foundSmall == null || foundFull == null) {
            foundSmall = FragmentPlayerSmall()
            foundFull = FragmentPlayerFull()

            val fragmentTransaction = mFragmentManager.beginTransaction()
            fragmentTransaction.hide(foundFull)
            fragmentTransaction.replace(R.id.fragment_player_small, foundSmall)
            fragmentTransaction.replace(R.id.fragment_player_full, foundFull)
            fragmentTransaction.commit()
        }

        smallPlayerFragment = foundSmall
        fullPlayerFragment = foundFull

        smallPlayerFragment.setCallback(object : FragmentPlayerSmall.Callback {
            override fun onToggle() { toggleBottomSheetState() }
        })
        fullPlayerFragment.setTouchInterceptListener(object : FragmentPlayerFull.TouchInterceptListener {
            override fun requestDisallowInterceptTouchEvent(disallow: Boolean) {
                findViewById<View>(R.id.bottom_sheet).parent.requestDisallowInterceptTouchEvent(disallow)
            }
        })

        val coordinatorLayoutParams = appBarLayout.layoutParams as CoordinatorLayout.LayoutParams
        val appBarLayoutBehavior = object : AppBarLayout.Behavior() {
            override fun onStartNestedScroll(parent: CoordinatorLayout, child: AppBarLayout, directTargetChild: View, target: View, nestedScrollAxes: Int, type: Int): Boolean {
                return playerBottomSheet.state == BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        coordinatorLayoutParams.behavior = appBarLayoutBehavior

        @Suppress("UNCHECKED_CAST")
        playerBottomSheet = BottomSheetBehavior.from(findViewById<View>(R.id.bottom_sheet)) as BottomSheetBehavior<View>
        playerBottomSheet.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            private var oldState = BottomSheetBehavior.STATE_COLLAPSED

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_DRAGGING && oldState == BottomSheetBehavior.STATE_EXPANDED) {
                    if (fullPlayerFragment.isScrolled()) {
                        playerBottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
                        return
                    }
                }

                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    if (smallPlayerFragment.context == null) return
                    appBarLayout.setExpanded(false)
                    smallPlayerFragment.setRole(FragmentPlayerSmall.Role.HEADER)

                    val ft = mFragmentManager.beginTransaction()
                    ft.hide(mFragmentManager.findFragmentById(R.id.containerView)!!)
                    ft.commit()
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    appBarLayout.setExpanded(true)
                    smallPlayerFragment.setRole(FragmentPlayerSmall.Role.PLAYER)
                    fullPlayerFragment.resetScroll()

                    val ft = mFragmentManager.beginTransaction()
                    ft.hide(fullPlayerFragment)
                    ft.commit()
                }

                if (oldState == BottomSheetBehavior.STATE_EXPANDED && newState != BottomSheetBehavior.STATE_EXPANDED) {
                    val ft = mFragmentManager.beginTransaction()
                    ft.show(mFragmentManager.findFragmentById(R.id.containerView)!!)
                    ft.commit()
                }

                if (oldState == BottomSheetBehavior.STATE_COLLAPSED && newState != oldState) {
                    fullPlayerFragment.init()

                    val ft = mFragmentManager.beginTransaction()
                    ft.show(fullPlayerFragment)
                    ft.commit()
                }

                oldState = newState
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        (application as RadioDroidApp).castHandler.onCreate(this)
        setupStartUpFragment()
    }

    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        selectedMenuItem = menuItem.itemId
        return navigateToSelectedMenuItem()
    }

    private fun navigateToSelectedMenuItem(): Boolean {
        if (playerBottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
            playerBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        mSearchView?.clearFocus()
        mDrawerLayout.closeDrawers()

        val backStackTag = selectedMenuItem.toString()
        val f: Fragment = when (selectedMenuItem) {
            R.id.nav_item_stations -> FragmentTabs()
            R.id.nav_item_starred -> FragmentStarred()
            R.id.nav_item_history -> FragmentHistory()
            R.id.nav_item_alarm -> FragmentAlarm()
            R.id.nav_item_settings -> FragmentSettings()
            else -> return false
        }

        mFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val fragmentTransaction = mFragmentManager.beginTransaction()
        if (Utils.bottomNavigationEnabled(this))
            fragmentTransaction.replace(R.id.containerView, f).commit()
        else
            fragmentTransaction.replace(R.id.containerView, f).addToBackStack(backStackTag).commit()

        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_HIDE_LOADING))
        invalidateOptionsMenu()
        checkMenuItems()
        appBarLayout.setExpanded(true)

        return false
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (playerBottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
            playerBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
            return
        }

        val backStackCount = mFragmentManager.backStackEntryCount

        if (backStackCount > 0) {
            val backStackEntry = mFragmentManager.getBackStackEntryAt(mFragmentManager.backStackEntryCount - 1)
            if (backStackEntry.name == "SearchPreferenceFragment") {
                super.onBackPressed()
                return
            }
            val parsedId = backStackEntry.name!!.toInt()
            if (parsedId == FRAGMENT_FROM_BACKSTACK) {
                super.onBackPressed()
                invalidateOptionsMenu()
                return
            }
        }

        if (Utils.bottomNavigationEnabled(this)) {
            if (lastExitTry != null && Date().time < lastExitTry!!.time + 3 * 1000) {
                PlayerServiceUtil.shutdownService()
                finish()
            } else {
                Toast.makeText(this, R.string.alert_press_back_to_exit, Toast.LENGTH_SHORT).show()
                lastExitTry = Date()
                return
            }
        }

        if (backStackCount > 1) {
            val backStackEntry = mFragmentManager.getBackStackEntryAt(mFragmentManager.backStackEntryCount - 2)
            selectedMenuItem = backStackEntry.name!!.toInt()

            if (!Utils.bottomNavigationEnabled(this)) {
                mNavigationView.setCheckedItem(selectedMenuItem)
            }
            invalidateOptionsMenu()
        } else {
            finish()
            return
        }
        super.onBackPressed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (BuildConfig.DEBUG) Log.d(TAG, "on request permissions result:$requestCode")

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERM_REQ_STORAGE_FAV_LOAD -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LoadFavourites()
                } else {
                    Log.w(TAG, "permission not granted -> simple load")
                    LoadFavouritesSimple()
                }
            }
            PERM_REQ_STORAGE_FAV_SAVE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    SaveFavourites()
                } else {
                    Log.w(TAG, "permission not granted -> simple save")
                    SaveFavouritesSimple()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!PlayerServiceUtil.isNotificationActive()) {
            PlayerServiceUtil.shutdownService()
        }
    }

    override fun onPause() {
        sharedPref.edit().putInt("last_selectedMenuItem", selectedMenuItem).apply()

        if (BuildConfig.DEBUG) Log.d(TAG, "PAUSED")

        broadcastReceiver?.let { LocalBroadcastManager.getInstance(this).unregisterReceiver(it) }

        super.onPause()

        if (PlayerServiceUtil.getPlayerState() == net.programmierecke.radiodroid2.players.PlayState.Idle) {
            PlayerServiceUtil.shutdownService()
        }

        val castHandler = (application as RadioDroidApp).castHandler
        castHandler.onPause()
        castHandler.setActivity(null)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val extras = intent.extras ?: return

        if (MediaSessionCallback.ACTION_PLAY_STATION_BY_UUID == action) {
            val context = applicationContext
            val stationUUID = extras.getString(MediaSessionCallback.EXTRA_STATION_UUID)
            if (TextUtils.isEmpty(stationUUID)) return
            intent.removeExtra(MediaSessionCallback.EXTRA_STATION_UUID)
            val radioDroidApp = application as RadioDroidApp
            val httpClient = radioDroidApp.httpClient

            @Suppress("DEPRECATION")
            object : AsyncTask<Void, Void, DataRadioStation?>() {
                override fun doInBackground(vararg params: Void): DataRadioStation? =
                    Utils.getStationByUuid(httpClient, context, stationUUID!!)

                override fun onPostExecute(station: DataRadioStation?) {
                    if (!isFinishing) {
                        if (station != null) {
                            Utils.showPlaySelection(radioDroidApp, station, supportFragmentManager)

                            val currentFragment = mFragmentManager.fragments.last()
                            if (currentFragment is FragmentHistory) {
                                currentFragment.RefreshListGui()
                            }
                        }
                    }
                }
            }.execute()
        } else {
            val searchTag = extras.getString(EXTRA_SEARCH_TAG)
            Log.d("MAIN", "received search request for tag 1: $searchTag")
            if (searchTag != null) {
                Log.d("MAIN", "received search request for tag 2: $searchTag")
                Search(StationsFilter.SearchStyle.ByTagExact, searchTag)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (BuildConfig.DEBUG) Log.d(TAG, "RESUMED")

        setupBroadcastReceiver()

        PlayerServiceUtil.startService(applicationContext)
        val castHandler = (application as RadioDroidApp).castHandler
        castHandler.onResume()
        castHandler.setActivity(this)

        if (playerBottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
            appBarLayout.setExpanded(false)
        }

        val intent = intent
        if (intent != null) {
            handleIntent(intent)
            setIntent(null)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_main, menu)

        val myToolbar = findViewById<Toolbar>(R.id.my_awesome_toolbar)
        menuItemSleepTimer = menu.findItem(R.id.action_set_sleep_timer)
        menuItemSearch = menu.findItem(R.id.action_search)
        menuItemDelete = menu.findItem(R.id.action_delete)
        menuItemSave = menu.findItem(R.id.action_save)
        menuItemLoad = menu.findItem(R.id.action_load)
        menuItemListView = menu.findItem(R.id.action_list_view)
        menuItemIconsView = menu.findItem(R.id.action_icons_view)
        menuItemAddAlarm = menu.findItem(R.id.action_add_alarm)
        menuItemMpd = menu.findItem(R.id.action_mpd)
        mSearchView = MenuItemCompat.getActionView(menuItemSearch) as SearchView
        mSearchView!!.setOnQueryTextListener(this)
        mSearchView!!.setOnQueryTextFocusChangeListener(object : View.OnFocusChangeListener {
            private var prevTabsVisibility = View.GONE
            override fun onFocusChange(v: View, hasFocus: Boolean) {
                if (Utils.bottomNavigationEnabled(this@ActivityMain)) {
                    mBottomNavigationView.visibility = if (hasFocus) View.GONE else View.VISIBLE
                }
                if (hasFocus) {
                    prevTabsVisibility = tabsView.visibility
                    tabsView.visibility = View.GONE
                } else {
                    tabsView.visibility = prevTabsVisibility
                }
            }
        })

        menuItemSleepTimer!!.isVisible = false
        menuItemSearch!!.isVisible = false
        menuItemDelete!!.isVisible = false
        menuItemSave!!.isVisible = false
        menuItemLoad!!.isVisible = false
        menuItemListView!!.isVisible = false
        menuItemIconsView!!.isVisible = false
        menuItemAddAlarm!!.isVisible = false

        var mpd_is_visible = false
        val radioDroidApp = application as? RadioDroidApp
        if (radioDroidApp != null) {
            val mpdClient: MPDClient = radioDroidApp.mpdClient
            val repository: MPDServersRepository = mpdClient.mpdServersRepository
            mpd_is_visible = !repository.isEmpty()
        }
        menuItemMpd!!.isVisible = mpd_is_visible

        when (selectedMenuItem) {
            R.id.nav_item_stations -> {
                menuItemSleepTimer!!.isVisible = true
                menuItemSearch!!.isVisible = true
                myToolbar.setTitle(R.string.nav_item_stations)
            }
            R.id.nav_item_starred -> {
                menuItemSleepTimer!!.isVisible = true
                menuItemSave!!.isVisible = true
                menuItemLoad!!.isVisible = true
                menuItemSave!!.setTitle(R.string.nav_item_save_playlist)

                if (sharedPref.getBoolean("icons_only_favorites_style", false)) {
                    menuItemListView!!.isVisible = true
                } else if (sharedPref.getBoolean("load_icons", false)) {
                    menuItemIconsView!!.isVisible = true
                }
                if (radioDroidApp!!.favouriteManager.isEmpty()) {
                    menuItemDelete!!.isVisible = false
                } else {
                    menuItemDelete!!.isVisible = true
                    menuItemDelete!!.setTitle(R.string.action_delete_favorites)
                }
                myToolbar.setTitle(R.string.nav_item_starred)
            }
            R.id.nav_item_history -> {
                menuItemSleepTimer!!.isVisible = true
                menuItemSave!!.isVisible = true
                menuItemSave!!.setTitle(R.string.nav_item_save_history_playlist)

                if (!radioDroidApp!!.historyManager.isEmpty()) {
                    menuItemDelete!!.isVisible = true
                    menuItemDelete!!.setTitle(R.string.action_delete_history)
                }
                myToolbar.setTitle(R.string.nav_item_history)
            }
            R.id.nav_item_alarm -> {
                menuItemAddAlarm!!.isVisible = true
                myToolbar.setTitle(R.string.nav_item_alarm)
            }
        }

        (application as RadioDroidApp).castHandler.getRouteItem(applicationContext, menu)

        return true
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)

        if (requestCode == ACTION_SAVE_FILE && resultCode == RESULT_OK) {
            if (resultData != null) {
                val uri = resultData.data
                Log.d(TAG, "Choosen save path: $uri")
                val radioDroidApp = application as RadioDroidApp
                val favouriteManager = radioDroidApp.favouriteManager
                val historyManager = radioDroidApp.historyManager
                try {
                    val os: OutputStream = contentResolver.openOutputStream(uri!!)!!
                    val writer = OutputStreamWriter(os)
                    if (selectedMenuItem == R.id.nav_item_starred) {
                        favouriteManager.SaveM3UWriter(writer)
                    } else if (selectedMenuItem == R.id.nav_item_history) {
                        historyManager.SaveM3UWriter(writer)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to write to file $e")
                }
            }
        }
        if (requestCode == ACTION_LOAD_FILE && resultCode == RESULT_OK) {
            if (resultData != null) {
                val uri = resultData.data
                Log.d(TAG, "Choosen load path: $uri")
                val radioDroidApp = application as RadioDroidApp
                val favouriteManager = radioDroidApp.favouriteManager
                try {
                    val `is`: InputStream = contentResolver.openInputStream(uri!!)!!
                    val reader = InputStreamReader(`is`)
                    favouriteManager.LoadM3USimple(reader)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to load to file $e")
                }
            }
        }
    }

    override fun onFileSelected(dialog: FileDialog, file: File) {
        try {
            Log.i("MAIN", "save to ${file.parent}/${file.name}")
            val radioDroidApp = application as RadioDroidApp
            val favouriteManager = radioDroidApp.favouriteManager
            val historyManager = radioDroidApp.historyManager

            if (dialog is SaveFileDialog) {
                if (selectedMenuItem == R.id.nav_item_starred) {
                    favouriteManager.SaveM3U(file.parent, file.name)
                } else if (selectedMenuItem == R.id.nav_item_history) {
                    historyManager.SaveM3U(file.parent, file.name)
                }
            } else if (dialog is OpenFileDialog) {
                favouriteManager.LoadM3U(file.parent, file.name)
            }
        } catch (e: Exception) {
            Log.e("MAIN", e.toString())
        }
    }

    fun SaveFavourites() {
        val dialog = SaveFileDialog()
        dialog.setStyle(DialogFragment.STYLE_NO_TITLE, Utils.getThemeResId(this))
        val args = Bundle()
        args.putString(FileDialog.EXTENSION, ".m3u")
        dialog.arguments = args
        dialog.show(supportFragmentManager, SaveFileDialog::class.java.name)
    }

    fun SaveFavouritesSimple() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_TITLE, "playlist.m3u")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, ACTION_SAVE_FILE)
    }

    fun LoadFavourites() {
        val dialogOpen = OpenFileDialog()
        dialogOpen.setStyle(DialogFragment.STYLE_NO_TITLE, Utils.getThemeResId(this))
        val argsOpen = Bundle()
        argsOpen.putString(FileDialog.EXTENSION, ".m3u")
        dialogOpen.arguments = argsOpen
        dialogOpen.show(supportFragmentManager, OpenFileDialog::class.java.name)
    }

    fun LoadFavouritesSimple() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_TITLE, "playlist.m3u")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, ACTION_LOAD_FILE)
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            android.R.id.home -> {
                mDrawerLayout.openDrawer(GravityCompat.START)
                return true
            }
            R.id.action_save -> {
                try {
                    if (Utils.verifyStoragePermissions(this, PERM_REQ_STORAGE_FAV_SAVE)) SaveFavourites()
                } catch (e: Exception) {
                    Log.e("MAIN", e.toString())
                }
                return true
            }
            R.id.action_load -> {
                try {
                    if (Utils.verifyStoragePermissions(this, PERM_REQ_STORAGE_FAV_LOAD)) LoadFavourites()
                } catch (e: Exception) {
                    Log.e("MAIN", e.toString())
                }
                return true
            }
            R.id.action_set_sleep_timer -> { changeTimer(); return true }
            R.id.action_mpd -> { selectMPDServer(); return true }
            R.id.action_delete -> {
                if (selectedMenuItem == R.id.nav_item_history) {
                    AlertDialog.Builder(this)
                        .setMessage(getString(R.string.alert_delete_history))
                        .setCancelable(true)
                        .setPositiveButton(getString(R.string.yes)) { _, _ ->
                            val historyManager = (application as RadioDroidApp).historyManager
                            historyManager.clear()
                            Toast.makeText(applicationContext, getString(R.string.notify_deleted_history), Toast.LENGTH_SHORT).show()
                            recreate()
                        }
                        .setNegativeButton(getString(R.string.no), null)
                        .show()
                }
                if (selectedMenuItem == R.id.nav_item_starred) {
                    AlertDialog.Builder(this)
                        .setMessage(getString(R.string.alert_delete_favorites))
                        .setCancelable(true)
                        .setPositiveButton(getString(R.string.yes)) { _, _ ->
                            val favouriteManager = (application as RadioDroidApp).favouriteManager
                            favouriteManager.clear()
                            Toast.makeText(applicationContext, getString(R.string.notify_deleted_favorites), Toast.LENGTH_SHORT).show()
                            recreate()
                        }
                        .setNegativeButton(getString(R.string.no), null)
                        .show()
                }
                return true
            }
            R.id.action_list_view -> { sharedPref.edit().putBoolean("icons_only_favorites_style", false).apply(); recreate(); return true }
            R.id.action_icons_view -> { sharedPref.edit().putBoolean("icons_only_favorites_style", true).apply(); recreate(); return true }
            R.id.action_add_alarm -> {
                val newFragment = TimePickerFragment()
                newFragment.setCallback(this)
                newFragment.show(supportFragmentManager, "timePicker")
                return true
            }
        }
        return super.onOptionsItemSelected(menuItem)
    }

    fun toggleBottomSheetState() {
        if (playerBottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
            playerBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        } else {
            playerBottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
        val radioDroidApp = application as RadioDroidApp
        val historyManager = radioDroidApp.historyManager
        val currentFragment = mFragmentManager.fragments[mFragmentManager.fragments.size - 2]
        if (historyManager.size() > 0 && currentFragment is FragmentAlarm) {
            val station = historyManager.getList()[0]
            currentFragment.getRam().add(station, hourOfDay, minute)
        }
    }

    private fun setupStartUpFragment() {
        if (instanceStateWasSaved) {
            invalidateOptionsMenu()
            checkMenuItems()
            return
        }

        val radioDroidApp = application as RadioDroidApp
        val hm = radioDroidApp.historyManager
        val fm = radioDroidApp.favouriteManager

        val startupAction = sharedPref.getString("startup_action", resources.getString(R.string.startup_show_history))!!

        if (startupAction == resources.getString(R.string.startup_show_history) && hm.isEmpty()) {
            selectMenuItem(R.id.nav_item_stations); return
        }
        if (startupAction == resources.getString(R.string.startup_show_favorites) && fm.isEmpty()) {
            selectMenuItem(R.id.nav_item_stations); return
        }

        when {
            startupAction == resources.getString(R.string.startup_show_history) -> selectMenuItem(R.id.nav_item_history)
            startupAction == resources.getString(R.string.startup_show_favorites) -> selectMenuItem(R.id.nav_item_starred)
            startupAction == resources.getString(R.string.startup_show_all_stations) || selectedMenuItem < 0 -> selectMenuItem(R.id.nav_item_stations)
            else -> selectMenuItem(selectedMenuItem)
        }
    }

    private fun selectMenuItem(itemId: Int) {
        val item = if (Utils.bottomNavigationEnabled(this))
            mBottomNavigationView.menu.findItem(itemId)
        else
            mNavigationView.menu.findItem(itemId)

        if (item != null) {
            onNavigationItemSelected(item)
        } else {
            selectedMenuItem = R.id.nav_item_stations
            navigateToSelectedMenuItem()
        }
    }

    private fun checkMenuItems() {
        mBottomNavigationView.menu.findItem(selectedMenuItem)?.isChecked = true
        mNavigationView.menu.findItem(selectedMenuItem)?.isChecked = true
    }

    fun Search(searchStyle: StationsFilter.SearchStyle, query: String) {
        Log.d("MAIN", "Search() searchstyle=$searchStyle query=$query")
        val currentFragment = mFragmentManager.fragments.last()
        if (currentFragment is FragmentTabs) {
            currentFragment.Search(searchStyle, query)
        } else {
            val backStackTag = R.id.nav_item_stations.toString()
            val f = FragmentTabs()
            val fragmentTransaction = mFragmentManager.beginTransaction()
            if (Utils.bottomNavigationEnabled(this)) {
                fragmentTransaction.replace(R.id.containerView, f).commit()
                mBottomNavigationView.menu.findItem(R.id.nav_item_stations).isChecked = true
            } else {
                fragmentTransaction.replace(R.id.containerView, f).addToBackStack(backStackTag).commit()
                mNavigationView.menu.findItem(R.id.nav_item_stations).isChecked = true
            }
            f.Search(searchStyle, query)
            selectedMenuItem = R.id.nav_item_stations
            invalidateOptionsMenu()
        }
    }

    fun SearchStations(query: String) {
        Log.d("MAIN", "SearchStations() $query")
        val currentFragment = mFragmentManager.fragments.last()
        if (currentFragment is IFragmentSearchable) {
            currentFragment.Search(StationsFilter.SearchStyle.ByName, query)
        }
    }

    override fun onQueryTextSubmit(query: String): Boolean = true

    override fun onQueryTextChange(newText: String): Boolean {
        SearchStations(newText)
        return true
    }

    private fun showMeteredConnectionDialog(playFunc: Runnable) {
        val res = resources
        val title = res.getString(R.string.alert_metered_connection_title)
        val text = res.getString(R.string.alert_metered_connection_message)
        meteredConnectionAlertDialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(text)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> playFunc.run() }
            .setOnDismissListener { meteredConnectionAlertDialog = null }
            .create()
        meteredConnectionAlertDialog!!.show()
    }

    private fun setupBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_HIDE_LOADING)
            addAction(ACTION_SHOW_LOADING)
            addAction(PlayerService.PLAYER_SERVICE_STATE_CHANGE)
            addAction(PlayerService.PLAYER_SERVICE_METERED_CONNECTION)
        }
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_HIDE_LOADING -> hideLoadingIcon()
                    ACTION_SHOW_LOADING -> showLoadingIcon()
                    PlayerService.PLAYER_SERVICE_METERED_CONNECTION -> {
                        meteredConnectionAlertDialog?.cancel()
                        meteredConnectionAlertDialog = null

                        val playerType = intent.getParcelableExtra<PlayerType>(PlayerService.PLAYER_SERVICE_METERED_CONNECTION_PLAYER_TYPE)!!
                        when (playerType) {
                            PlayerType.RADIODROID -> showMeteredConnectionDialog(Runnable {
                                Utils.play(application as RadioDroidApp, PlayerServiceUtil.getCurrentStation()!!)
                            })
                            PlayerType.EXTERNAL -> {
                                val currentStation = PlayerServiceUtil.getCurrentStation()
                                if (currentStation != null) {
                                    showMeteredConnectionDialog(Runnable {
                                        @Suppress("DEPRECATION")
                                        PlayStationTask.playExternal(currentStation, this@ActivityMain).execute()
                                    })
                                }
                            }
                            else -> Log.e(TAG, "broadcastReceiver unexpected PlayerType '$playerType'")
                        }
                    }
                    PlayerService.PLAYER_SERVICE_STATE_CHANGE -> {
                        if (PlayerServiceUtil.isPlaying()) {
                            meteredConnectionAlertDialog?.cancel()
                            meteredConnectionAlertDialog = null
                        }
                    }
                }
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver!!, filter)
    }

    private fun showLoadingIcon() {
        findViewById<View>(R.id.progressBarLoading).visibility = View.VISIBLE
    }

    private fun hideLoadingIcon() {
        findViewById<View>(R.id.progressBarLoading).visibility = View.GONE
    }

    private fun changeTimer() {
        val seekDialog = AlertDialog.Builder(this)
        val seekView = View.inflate(this, R.layout.layout_timer_chooser, null)

        seekDialog.setTitle(R.string.sleep_timer_title)
        seekDialog.setView(seekView)

        val seekTextView = seekView.findViewById<TextView>(R.id.timerTextView)
        val seekBar = seekView.findViewById<SeekBar>(R.id.timerSeekBar)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                seekTextView.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        val currenTimerSeconds = PlayerServiceUtil.getTimerSeconds()
        val currentTimer = when {
            currenTimerSeconds <= 0 -> sharedPref.getInt("sleep_timer_default_minutes", 10).toLong()
            currenTimerSeconds < 60 -> 1L
            else -> currenTimerSeconds / 60
        }
        seekBar.progress = currentTimer.toInt()

        seekDialog.setPositiveButton(R.string.sleep_timer_apply) { _, _ ->
            PlayerServiceUtil.clearTimer()
            PlayerServiceUtil.addTimer(seekBar.progress * 60)
            sharedPref.edit().putInt("sleep_timer_default_minutes", seekBar.progress).apply()
        }

        seekDialog.setNegativeButton(R.string.sleep_timer_clear) { _, _ ->
            PlayerServiceUtil.clearTimer()
        }

        seekDialog.create()
        seekDialog.show()
    }

    private fun selectMPDServer() {
        val radioDroidApp = application as RadioDroidApp
        Utils.showMpdServersDialog(radioDroidApp, supportFragmentManager, null)
    }

    fun getToolbar(): Toolbar = findViewById(R.id.my_awesome_toolbar)

    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        result.closeSearchPage(this)
        supportFragmentManager.popBackStack()
        val f = FragmentSettings.openNewSettingsSubFragment(this, result.screen)
        result.highlight(f)
    }

    override fun invalidateOptionsMenuForCast() {
        invalidateOptionsMenu()
    }
}
