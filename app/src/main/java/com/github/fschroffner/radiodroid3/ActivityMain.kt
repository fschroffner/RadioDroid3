package com.github.fschroffner.radiodroid3

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener
import com.github.fschroffner.radiodroid3.cast.CastAwareActivity
import com.github.fschroffner.radiodroid3.data.datastore.AppPreferencesDataStore
import com.github.fschroffner.radiodroid3.navigation.MainNavigationController
import com.github.fschroffner.radiodroid3.player.MediaControllerRepository
import com.github.fschroffner.radiodroid3.players.PlayState
import com.github.fschroffner.radiodroid3.players.selector.PlayerType
import com.github.fschroffner.radiodroid3.station.StationsFilter
import com.github.fschroffner.radiodroid3.ui.player.PlayerBottomSheetManager
import com.github.fschroffner.radiodroid3.ui.player.SleepTimerDialogHelper
import com.github.fschroffner.radiodroid3.ui.playlist.PlaylistFileManager
import com.github.fschroffner.radiodroid3.ui.search.MainSearchManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.mikepenz.iconics.Iconics
import com.rustamg.filedialogs.FileDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject

/**
 * Main application activity acting as a lightweight host container.
 * Responsibilities (Navigation, BottomSheet, Playlists, Search) are delegated to focused controllers.
 */
@AndroidEntryPoint
class ActivityMain : AppCompatActivity(),
    SearchView.OnQueryTextListener,
    NavigationView.OnNavigationItemSelectedListener,
    BottomNavigationView.OnNavigationItemSelectedListener,
    FileDialog.OnFileSelectedListener,
    SearchPreferenceResultListener,
    CastAwareActivity {

    @Inject lateinit var historyManager: HistoryManager
    @Inject lateinit var favouriteManager: FavouriteManager
    @Inject lateinit var castHandler: CastHandler
    @Inject lateinit var fallbackStationsManager: FallbackStationsManager
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var appPreferencesDataStore: AppPreferencesDataStore
    @Inject lateinit var mediaControllerRepository: MediaControllerRepository
    @Inject lateinit var stationPlaybackUseCase: com.github.fschroffner.radiodroid3.player.StationPlaybackUseCase
    @Inject lateinit var loadingStateHolder: com.github.fschroffner.radiodroid3.data.loading.LoadingStateHolder

    companion object {
        const val EXTRA_SEARCH_TAG = "search_tag"
        const val LAUNCH_EQUALIZER_REQUEST = 1
        const val MAX_DYNAMIC_LAUNCHER_SHORTCUTS = 4
        const val FRAGMENT_FROM_BACKSTACK = 777
        const val ACTION_SHOW_LOADING = "com.github.fschroffner.radiodroid3.show_loading"
        const val ACTION_HIDE_LOADING = "com.github.fschroffner.radiodroid3.hide_loading"
        const val ACTION_PLAY_STATION_BY_UUID = "PLAY_STATION_BY_UUID"
        const val EXTRA_STATION_UUID = "STATION_UUID"
        private const val TAG = "RadioDroid"
        const val PERM_REQ_STORAGE_FAV_SAVE = 1
        const val PERM_REQ_STORAGE_FAV_LOAD = 2
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.w(TAG, "Notification permission denied.")
        }
    }

    lateinit var mDrawerLayout: DrawerLayout
    lateinit var mNavigationView: NavigationView
    lateinit var mBottomNavigationView: BottomNavigationView
    lateinit var mFragmentManager: FragmentManager
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var tabsView: TabLayout

    lateinit var navigationController: MainNavigationController
        private set
    lateinit var playerBottomSheetManager: PlayerBottomSheetManager
        private set
    lateinit var playlistFileManager: PlaylistFileManager
        private set
    lateinit var searchManager: MainSearchManager
        private set

    var broadcastReceiver: BroadcastReceiver? = null

    var menuItemSearch: MenuItem? = null
    var menuItemDelete: MenuItem? = null
    var menuItemSleepTimer: MenuItem? = null
    var menuItemSave: MenuItem? = null
    var menuItemLoad: MenuItem? = null
    var menuItemIconsView: MenuItem? = null
    var menuItemListView: MenuItem? = null
    private var mSearchView: SearchView? = null

    private lateinit var sharedPref: SharedPreferences
    private var meteredConnectionAlertDialog: AlertDialog? = null

    val selectedMenuItem: Int
        get() = if (::navigationController.isInitialized) navigationController.selectedMenuItem else -1

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        Utils.applyTheme(this)
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        Iconics.init(this)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.layout_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_content)) { v, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            WindowInsetsCompat.Builder(windowInsets)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars(),
                    androidx.core.graphics.Insets.of(systemBars.left, 0, systemBars.right, systemBars.bottom)
                )
                .build()
        }
        ViewCompat.requestApplyInsets(findViewById(R.id.main_content))

        cleanInternalFilesDirectory()

        val myToolbar = findViewById<Toolbar>(R.id.my_awesome_toolbar)
        setSupportActionBar(myToolbar)

        mediaControllerRepository.connect()

        mFragmentManager = supportFragmentManager
        appBarLayout = findViewById(R.id.app_bar_layout)
        tabsView = findViewById(R.id.tabs)
        mDrawerLayout = findViewById(R.id.drawerLayout)
        mNavigationView = findViewById(R.id.my_navigation_view)
        mBottomNavigationView = findViewById(R.id.bottom_navigation)

        setupNavigationViews()
        setupControllers()

        castHandler.onCreate(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        observePlaybackAndConnectionState()
        navigationController.setupStartUpFragment(savedInstanceState != null)
        setupBackNavigation()
    }

    private fun setupControllers() {
        playerBottomSheetManager = PlayerBottomSheetManager(
            activity = this,
            fragmentManager = mFragmentManager,
            bottomSheetView = findViewById(R.id.bottom_sheet),
            appBarLayout = appBarLayout,
            containerViewId = R.id.containerView
        )
        playerBottomSheetManager.setup()

        navigationController = MainNavigationController(
            activity = this,
            fragmentManager = mFragmentManager,
            drawerLayout = mDrawerLayout,
            navigationView = mNavigationView,
            bottomNavigationView = mBottomNavigationView,
            appBarLayout = appBarLayout,
            sharedPref = sharedPref,
            historyManager = historyManager,
            favouriteManager = favouriteManager,
            onBottomSheetCollapseRequested = { playerBottomSheetManager.collapse() },
            onServiceShutdownRequested = { mediaControllerRepository.shutdownService() }
        )

        playlistFileManager = PlaylistFileManager(
            activity = this,
            fragmentManager = mFragmentManager,
            favouriteManager = favouriteManager,
            historyManager = historyManager,
            selectedMenuItemProvider = { navigationController.selectedMenuItem }
        )

        searchManager = MainSearchManager(
            context = this,
            fragmentManager = mFragmentManager,
            tabsView = tabsView,
            bottomNavigationView = mBottomNavigationView
        )
    }

    private fun setupNavigationViews() {
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

            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeButtonEnabled(true)
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!navigationController.handleBackPressed()) {
                    finish()
                }
            }
        })
    }

    private fun observePlaybackAndConnectionState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    loadingStateHolder.isLoading.collect { isLoading ->
                        findViewById<View>(R.id.progressBarLoading)?.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    mediaControllerRepository.playbackState.collect { state ->
                        if (state.isPlaying) {
                            meteredConnectionAlertDialog?.cancel()
                            meteredConnectionAlertDialog = null
                        }
                    }
                }
                launch {
                    mediaControllerRepository.meteredConnectionEvents.collect { playerType ->
                        meteredConnectionAlertDialog?.cancel()
                        meteredConnectionAlertDialog = null
                        when (playerType) {
                            PlayerType.RADIODROID -> showMeteredConnectionDialog {
                                mediaControllerRepository.getCurrentStation()?.let { stationPlaybackUseCase.playStation(it) }
                            }
                            PlayerType.EXTERNAL -> {
                                val currentStation = mediaControllerRepository.getCurrentStation()
                                if (currentStation != null) {
                                    showMeteredConnectionDialog {
                                        stationPlaybackUseCase.playExternal(currentStation, this@ActivityMain)
                                    }
                                }
                            }
                            else -> Log.e(TAG, "unexpected PlayerType '$playerType'")
                        }
                    }
                }
            }
        }
    }

    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        searchManager.clearFocus()
        return navigationController.onNavigationItemSelected(menuItem.itemId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        playlistFileManager.handlePermissionsResult(requestCode, grantResults)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!mediaControllerRepository.playbackState.value.isNotificationActive) {
            mediaControllerRepository.shutdownService()
        }
    }

    override fun onPause() {
        navigationController.saveSelectedMenuItem()
        broadcastReceiver?.let { LocalBroadcastManager.getInstance(this).unregisterReceiver(it) }
        super.onPause()

        if (mediaControllerRepository.playbackState.value.playState == PlayState.Idle) {
            mediaControllerRepository.shutdownService()
        }

        castHandler.onPause()
        castHandler.setActivity(null)
    }

    override fun onResume() {
        super.onResume()
        setupBroadcastReceiver()
        mediaControllerRepository.connect()

        castHandler.onResume()
        castHandler.setActivity(this)

        if (playerBottomSheetManager.isExpanded()) {
            appBarLayout.setExpanded(false)
        }

        intent?.let {
            handleIntent(it)
            setIntent(null)
        }
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val extras = intent.extras ?: return

        if (ACTION_PLAY_STATION_BY_UUID == action) {
            val stationUUID = extras.getString(EXTRA_STATION_UUID)
            if (stationUUID.isNullOrEmpty()) return
            intent.removeExtra(EXTRA_STATION_UUID)

            val httpClient = okHttpClient
            val context = applicationContext

            lifecycleScope.launch {
                val station = withContext(Dispatchers.IO) {
                    Utils.getStationByUuid(httpClient, context, stationUUID)
                }
                if (!isFinishing && station != null) {
                    stationPlaybackUseCase.showPlaySelection(this@ActivityMain, station, supportFragmentManager)

                    val currentFragment = mFragmentManager.fragments.lastOrNull()
                    if (currentFragment is FragmentHistory) {
                        currentFragment.RefreshListGui()
                    }
                }
            }
        } else {
            val searchTag = extras.getString(EXTRA_SEARCH_TAG)
            if (searchTag != null) {
                Search(StationsFilter.SearchStyle.ByTagExact, searchTag)
            }
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

        mSearchView = searchManager.setupSearchMenuItem(menuItemSearch!!)

        menuItemSleepTimer?.isVisible = false
        menuItemSearch?.isVisible = false
        menuItemDelete?.isVisible = false
        menuItemSave?.isVisible = false
        menuItemLoad?.isVisible = false
        menuItemListView?.isVisible = false
        menuItemIconsView?.isVisible = false

        when (navigationController.selectedMenuItem) {
            R.id.nav_item_stations -> {
                menuItemSleepTimer?.isVisible = true
                menuItemSearch?.isVisible = true
                myToolbar.setTitle(R.string.nav_item_stations)
            }
            R.id.nav_item_starred -> {
                menuItemSleepTimer?.isVisible = true
                menuItemSave?.isVisible = true
                menuItemLoad?.isVisible = true
                menuItemSave?.setTitle(R.string.nav_item_save_playlist)

                if (sharedPref.getBoolean("icons_only_favorites_style", false)) {
                    menuItemListView?.isVisible = true
                } else if (sharedPref.getBoolean("load_icons", false)) {
                    menuItemIconsView?.isVisible = true
                }
                if (favouriteManager.isEmpty()) {
                    menuItemDelete?.isVisible = false
                } else {
                    menuItemDelete?.isVisible = true
                    menuItemDelete?.setTitle(R.string.action_delete_favorites)
                }
                myToolbar.setTitle(R.string.nav_item_starred)
            }
            R.id.nav_item_history -> {
                menuItemSleepTimer?.isVisible = true
                menuItemSave?.isVisible = true
                menuItemSave?.setTitle(R.string.nav_item_save_history_playlist)

                if (!historyManager.isEmpty()) {
                    menuItemDelete?.isVisible = true
                    menuItemDelete?.setTitle(R.string.action_delete_history)
                }
                myToolbar.setTitle(R.string.nav_item_history)
            }
        }

        castHandler.getRouteItem(applicationContext, menu)
        return true
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            android.R.id.home -> {
                mDrawerLayout.openDrawer(GravityCompat.START)
                return true
            }
            R.id.action_save -> {
                try {
                    if (Utils.verifyStoragePermissions(this, PERM_REQ_STORAGE_FAV_SAVE)) {
                        playlistFileManager.saveFavourites()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, e.toString())
                }
                return true
            }
            R.id.action_load -> {
                try {
                    if (Utils.verifyStoragePermissions(this, PERM_REQ_STORAGE_FAV_LOAD)) {
                        playlistFileManager.loadFavourites()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, e.toString())
                }
                return true
            }
            R.id.action_set_sleep_timer -> {
                SleepTimerDialogHelper.show(this, mediaControllerRepository, sharedPref)
                return true
            }
            R.id.action_delete -> {
                showDeleteConfirmationDialog()
                return true
            }
            R.id.action_list_view -> {
                appPreferencesDataStore.putBooleanAsync("icons_only_favorites_style", false)
                sharedPref.edit().putBoolean("icons_only_favorites_style", false).apply()
                recreate()
                return true
            }
            R.id.action_icons_view -> {
                appPreferencesDataStore.putBooleanAsync("icons_only_favorites_style", true)
                sharedPref.edit().putBoolean("icons_only_favorites_style", true).apply()
                recreate()
                return true
            }
        }
        return super.onOptionsItemSelected(menuItem)
    }

    private fun showDeleteConfirmationDialog() {
        if (navigationController.selectedMenuItem == R.id.nav_item_history) {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.alert_delete_history))
                .setCancelable(true)
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    historyManager.clear()
                    Toast.makeText(applicationContext, getString(R.string.notify_deleted_history), Toast.LENGTH_SHORT).show()
                    recreate()
                }
                .setNegativeButton(getString(R.string.no), null)
                .show()
        } else if (navigationController.selectedMenuItem == R.id.nav_item_starred) {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.alert_delete_favorites))
                .setCancelable(true)
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    favouriteManager.clear()
                    Toast.makeText(applicationContext, getString(R.string.notify_deleted_favorites), Toast.LENGTH_SHORT).show()
                    recreate()
                }
                .setNegativeButton(getString(R.string.no), null)
                .show()
        }
    }

    fun toggleBottomSheetState() = playerBottomSheetManager.toggleBottomSheetState()

    fun Search(searchStyle: StationsFilter.SearchStyle, query: String) =
        navigationController.navigateToSearch(searchStyle, query)

    fun SearchStations(query: String) = searchManager.searchStations(query)

    override fun onQueryTextSubmit(query: String): Boolean = searchManager.onQueryTextSubmit(query)

    override fun onQueryTextChange(newText: String): Boolean = searchManager.onQueryTextChange(newText)

    override fun onFileSelected(dialog: FileDialog, file: File) =
        playlistFileManager.onFileSelected(dialog, file)

    fun SaveFavourites() = playlistFileManager.saveFavourites()
    fun SaveFavouritesSimple() = playlistFileManager.saveFavouritesSimple()
    fun LoadFavourites() = playlistFileManager.loadFavourites()
    fun LoadFavouritesSimple() = playlistFileManager.loadFavouritesSimple()

    fun getToolbar(): Toolbar? = findViewById(R.id.my_awesome_toolbar)

    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        result.closeSearchPage(this)
        supportFragmentManager.popBackStack()
        val f = FragmentSettings.openNewSettingsSubFragment(this, result.screen)
        result.highlight(f)
    }

    override fun invalidateOptionsMenuForCast() {
        invalidateOptionsMenu()
    }

    private fun showMeteredConnectionDialog(playAction: () -> Unit) {
        val title = resources.getString(R.string.alert_metered_connection_title)
        val text = resources.getString(R.string.alert_metered_connection_message)
        meteredConnectionAlertDialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(text)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> playAction() }
            .setOnDismissListener { meteredConnectionAlertDialog = null }
            .create()
        meteredConnectionAlertDialog?.show()
    }

    private fun setupBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_HIDE_LOADING)
            addAction(ACTION_SHOW_LOADING)
        }
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_HIDE_LOADING -> hideLoadingIcon()
                    ACTION_SHOW_LOADING -> showLoadingIcon()
                }
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver!!, filter)
    }

    private fun showLoadingIcon() {
        loadingStateHolder.showLoading()
    }

    private fun hideLoadingIcon() {
        loadingStateHolder.hideLoading()
    }

    private fun cleanInternalFilesDirectory() {
        try {
            val dir = filesDir
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.name.endsWith(".tmp") || file.name.startsWith("temp_"))) {
                        try { file.delete() } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
