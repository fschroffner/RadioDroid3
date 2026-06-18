package net.programmierecke.radiodroid2

import android.app.UiModeManager
import android.content.res.Configuration
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import net.programmierecke.radiodroid2.alarm.RadioAlarmManager
import net.programmierecke.radiodroid2.history.TrackHistoryRepository
import net.programmierecke.radiodroid2.players.mpd.MPDClient
import net.programmierecke.radiodroid2.proxy.ProxySettings
import net.programmierecke.radiodroid2.recording.RecordingsManager
import net.programmierecke.radiodroid2.station.live.metadata.TrackMetadataSearcher
import net.programmierecke.radiodroid2.utils.TvChannelManager
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class RadioDroidApp : MultiDexApplication() {

    lateinit var historyManager: HistoryManager
        private set
    lateinit var favouriteManager: FavouriteManager
        private set
    lateinit var recordingsManager: RecordingsManager
        private set
    lateinit var fallbackStationsManager: FallbackStationsManager
        private set
    lateinit var alarmManager: RadioAlarmManager
        private set
    private var tvChannelManager: TvChannelManager? = null

    lateinit var trackHistoryRepository: TrackHistoryRepository
        private set
    lateinit var mpdClient: MPDClient
        private set
    lateinit var castHandler: CastHandler
        private set
    lateinit var trackMetadataSearcher: TrackMetadataSearcher
        private set

    private val connectionPool = ConnectionPool()
    lateinit var httpClient: OkHttpClient
        private set

    var testsInterceptor: Interceptor? = null

    inner class UserAgentInterceptor(private val userAgent: String) : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val requestWithUserAgent = chain.request().newBuilder().header("User-Agent", userAgent).build()
            return chain.proceed(requestWithUserAgent)
        }
    }

    companion object {
        init {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }
    }

    override fun onCreate() {
        super.onCreate()

        GoogleProviderHelper.use(baseContext)

        rebuildHttpClient()

        val picassoInstance = Picasso.Builder(this)
            .downloader(OkHttp3Downloader(newHttpClientForPicasso()))
            .build()
        Picasso.setSingletonInstance(picassoInstance)

        CountryCodeDictionary.getInstance().load(this)
        CountryFlagsLoader.getInstance()

        historyManager = HistoryManager(this)
        favouriteManager = FavouriteManager(this)
        fallbackStationsManager = FallbackStationsManager(this)
        recordingsManager = RecordingsManager()
        alarmManager = RadioAlarmManager(this)

        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            tvChannelManager = TvChannelManager(this)
            favouriteManager.addObserver(tvChannelManager)
        }

        trackHistoryRepository = TrackHistoryRepository(this)
        mpdClient = MPDClient(this)
        castHandler = CastHandler()
        trackMetadataSearcher = TrackMetadataSearcher(httpClient)

        recordingsManager.updateRecordingsList()
    }

    fun rebuildHttpClient() {
        httpClient = newHttpClient()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor("RadioDroid2/${BuildConfig.VERSION_NAME}"))
            .build()
    }

    fun newHttpClient(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder().connectionPool(connectionPool)
        testsInterceptor?.let { builder.addInterceptor(it) }
        if (!setCurrentOkHttpProxy(builder)) {
            Toast.makeText(this, resources.getString(R.string.ignore_proxy_settings_invalid), Toast.LENGTH_SHORT).show()
        }
        return Utils.enableTls12OnPreLollipop(builder)
    }

    fun newHttpClientWithoutProxy(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder().connectionPool(connectionPool)
        testsInterceptor?.let { builder.addInterceptor(it) }
        return Utils.enableTls12OnPreLollipop(builder)
    }

    fun setCurrentOkHttpProxy(builder: OkHttpClient.Builder): Boolean {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        val proxySettings = ProxySettings.fromPreferences(sharedPref)
        if (proxySettings != null) {
            if (!Utils.setOkHttpProxy(builder, proxySettings)) return false
        }
        return true
    }

    private fun newHttpClientForPicasso(): OkHttpClient {
        val cache = File(cacheDir, "picasso-cache").also { if (!it.exists()) it.mkdirs() }
        val builder = OkHttpClient.Builder()
            .addInterceptor(UserAgentInterceptor("RadioDroid2/${BuildConfig.VERSION_NAME}"))
            .cache(Cache(cache, Int.MAX_VALUE.toLong()))
        testsInterceptor?.let { builder.addInterceptor(it) }
        setCurrentOkHttpProxy(builder)
        return builder.build()
    }
}
