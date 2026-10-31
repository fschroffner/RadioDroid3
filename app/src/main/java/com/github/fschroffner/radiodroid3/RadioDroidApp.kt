package com.github.fschroffner.radiodroid3

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import dagger.hilt.android.HiltAndroidApp
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import com.github.fschroffner.radiodroid3.utils.TvChannelManager
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class RadioDroidApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var favouriteManager: FavouriteManager

    private var tvChannelManager: TvChannelManager? = null

    companion object {
        var instance: RadioDroidApp? = null
            private set

        init {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { okHttpClient }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "coil_image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val nightMode = when (Utils.getThemeResId(this)) {
            R.style.MyMaterialTheme_Dark, R.style.MyMaterialTheme_Oled -> AppCompatDelegate.MODE_NIGHT_YES
            else -> {
                val theme = Utils.getTheme(this)
                if (theme.contains("system", ignoreCase = true)) {
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                }
            }
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)

        GoogleProviderHelper.use(baseContext)

        val cache = File(cacheDir, "picasso-cache").also { if (!it.exists()) it.mkdirs() }
        val picassoOkHttpClient = okHttpClient.newBuilder()
            .cache(Cache(cache, Int.MAX_VALUE.toLong()))
            .build()
        val picassoInstance = Picasso.Builder(this)
            .downloader(OkHttp3Downloader(picassoOkHttpClient))
            .build()
        try {
            Picasso.setSingletonInstance(picassoInstance)
        } catch (_: Exception) {}

        CountryCodeDictionary.instance.load(this)
        CountryFlagsLoader.instance

        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            val tv = TvChannelManager(this, favouriteManager)
            tvChannelManager = tv
            favouriteManager.addObserver(tv)
        }
    }
}
