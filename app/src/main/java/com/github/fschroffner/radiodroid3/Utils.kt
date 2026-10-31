package com.github.fschroffner.radiodroid3

import com.github.fschroffner.radiodroid3.utils.ThemeUtils
import com.github.fschroffner.radiodroid3.utils.NetworkUtils
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import coil.load
import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.webkit.MimeTypeMap
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.mikepenz.iconics.IconicsColor
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.IIcon
import com.github.fschroffner.radiodroid3.players.selector.PlayerSelectorDialog
import com.github.fschroffner.radiodroid3.players.selector.PlayerType
import com.github.fschroffner.radiodroid3.proxy.ProxySettings
import com.github.fschroffner.radiodroid3.service.ConnectivityChecker
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import com.github.fschroffner.radiodroid3.utils.Tls12SocketFactory
import okhttp3.Authenticator
import okhttp3.ConnectionSpec
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.TlsVersion
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.KeyStore
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object Utils {
    private var loadIcons = -1

    @JvmStatic
    fun parseIntWithDefault(number: String?, defaultVal: Int): Int {
        return try {
            number?.toInt() ?: defaultVal
        } catch (e: NumberFormatException) {
            defaultVal
        }
    }

    @JvmStatic
    fun getCacheFile(ctx: Context, theURI: String): String? {
        return try {
            var aFileName = theURI.lowercase().replace("http://", "")
            aFileName = aFileName.lowercase().replace("https://", "")
            aFileName = sanitizeName(aFileName)

            val file = File(ctx.cacheDir, aFileName)
            if (!file.exists() || !file.isFile) return null

            val millis = System.currentTimeMillis() - file.lastModified()
            val hours = millis / (1000 * 60 * 60)

            if (hours < 1) {
                if (BuildConfig.DEBUG) Log.d("UTIL", "used cache for:$theURI")
                file.readText(Charsets.UTF_8)
            } else {
                if (BuildConfig.DEBUG) Log.d("UTIL", "do not use cache, because too old:$theURI")
                null
            }
        } catch (e: Exception) {
            Log.e("UTIL", "getCacheFile() $e")
            null
        }
    }

    @JvmStatic
    fun writeFileCache(ctx: Context, theURI: String, content: String) {
        try {
            var aFileName = theURI.lowercase().replace("http://", "")
            aFileName = aFileName.lowercase().replace("https://", "")
            aFileName = sanitizeName(aFileName)

            val f = File(ctx.cacheDir, aFileName)
            f.writeText(content, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("UTIL", "writeFileCache() could not write to cache file for:$theURI")
        }
    }

    private fun downloadFeed(httpClient: OkHttpClient, ctx: Context, theURI: String, forceUpdate: Boolean, dictParams: Map<String, String>?): String? {
        Log.i("DOWN", "Url=$theURI")
        if (!forceUpdate) {
            val cache = getCacheFile(ctx, theURI)
            if (cache != null) return cache
        }
        Log.i("DOWN", "Url=$theURI (not cached)")

        return try {
            val requestBuilder = Request.Builder().url(theURI)

            if (dictParams != null) {
                val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val gson = Gson()
                val json = gson.toJson(dictParams)
                val requestBody = RequestBody.create(jsonMediaType, json)
                requestBuilder.post(requestBody)
            } else {
                requestBuilder.get()
            }

            val request = requestBuilder.build()
            httpClient.newCall(request).execute().use { response ->
                val responseStr = response.body?.string()

                if (!response.isSuccessful || responseStr == null) {
                    Log.e("UTIL", "Unsuccessful response: ${response.code} ${response.message}\n$responseStr")
                    return null
                }

                writeFileCache(ctx, theURI, responseStr)
                if (BuildConfig.DEBUG) Log.d("UTIL", "wrote cache file for:$theURI")
                responseStr
            }
        } catch (e: Exception) {
            Log.e("UTIL", "downloadFeed() $e")
            null
        }
    }

    @JvmStatic
    fun downloadFeedRelative(httpClient: OkHttpClient, ctx: Context, theRelativeUri: String, forceUpdate: Boolean, dictParams: Map<String, String>?): String? {
        val currentServer = RadioBrowserServerManager.getCurrentServer() ?: return null

        var endpoint = RadioBrowserServerManager.constructEndpoint(currentServer, theRelativeUri)
        var result = downloadFeed(httpClient, ctx, endpoint, forceUpdate, dictParams)
        if (result != null) return result

        val serverList = RadioBrowserServerManager.getServerList(false)
        for (newServer in serverList) {
            if (newServer == currentServer) continue
            endpoint = RadioBrowserServerManager.constructEndpoint(newServer, theRelativeUri)
            result = downloadFeed(httpClient, ctx, endpoint, forceUpdate, dictParams)
            if (result != null) {
                RadioBrowserServerManager.setCurrentServer(newServer)
                return result
            }
        }

        return null
    }

    @JvmStatic
    fun getRealStationLink(httpClient: OkHttpClient, ctx: Context, stationId: String): String? {
        Log.i("UTIL", "StationUUID:$stationId")
        val result = downloadFeedRelative(httpClient, ctx, "json/url/$stationId", true, null)
        if (result != null) {
            Log.i("UTIL", result)
            return try {
                JSONObject(result).getString("url")
            } catch (e: Exception) {
                Log.e("UTIL", "getRealStationLink() $e")
                null
            }
        }
        return null
    }

    @Deprecated("")
    @JvmStatic
    fun getStationById(httpClient: OkHttpClient, ctx: Context, stationId: String): DataRadioStation? {
        Log.w("UTIL", "Search by id:$stationId")
        val result = downloadFeed(httpClient, ctx, "json/stations/byid/$stationId", true, null)
        if (result != null) {
            return try {
                val list = DataRadioStation.DecodeJson(result)
                if (list != null) {
                    if (list.size == 1) return list[0]
                    Log.e("UTIL", "stations by id did have length:${list.size}")
                }
                null
            } catch (e: Exception) {
                Log.e("UTIL", "getStationByid() $e")
                null
            }
        }
        return null
    }

    @JvmStatic
    fun getStationByUuid(httpClient: OkHttpClient, ctx: Context, stationUuid: String): DataRadioStation? {
        Log.w("UTIL", "Search by uuid:$stationUuid")
        val result = downloadFeedRelative(httpClient, ctx, "json/stations/byuuid/$stationUuid", true, null)
        if (result != null) {
            return try {
                val list = DataRadioStation.DecodeJson(result)
                if (list != null) {
                    if (list.size == 1) return list[0]
                    Log.e("UTIL", "stations by uuid did have length:${list.size}")
                }
                null
            } catch (e: Exception) {
                Log.e("UTIL", "getStationByUuid() $e")
                null
            }
        }
        return null
    }

    @JvmStatic
    fun getStationsByUuid(httpClient: OkHttpClient, ctx: Context, listUUids: Iterable<String>): List<DataRadioStation>? {
        val uuids = TextUtils.join(",", listUUids)
        Log.d("UTIL", "Search by uuid for items")
        val p = hashMapOf("uuids" to uuids)
        val result = downloadFeedRelative(httpClient, ctx, "json/stations/byuuid", true, p)
        if (result != null) {
            return try {
                val list = DataRadioStation.DecodeJson(result)
                if (list != null) list
                else {
                    Log.e("UTIL", "stations by uuid was null")
                    null
                }
            } catch (e: Exception) {
                Log.e("UTIL", "getStationsByUuid() $e")
                null
            }
        }
        return null
    }

    @JvmStatic
    fun refreshStation(station: DataRadioStation, httpClient: okhttp3.OkHttpClient, context: Context): Boolean {
        @Suppress("DEPRECATION")
        val refreshed = if (!TextUtils.isEmpty(station.StationUuid)) getStationByUuid(httpClient, context, station.StationUuid)
                        else getStationById(httpClient, context, station.StationId)
        return if (refreshed != null && refreshed.hasValidUuid()) {
            station.copyPropertiesFrom(refreshed)
            station.RefreshRetryCount = 0
            true
        } else {
            if (hasAnyConnection(context)) station.RefreshRetryCount++
            false
        }
    }

    @JvmStatic
    fun loadStationIcon(holder: ImageView, fromUrl: String?) {
        if (fromUrl.isNullOrBlank()) return
        holder.load(fromUrl) {
            placeholder(R.drawable.ic_photo_24dp)
            error(R.drawable.ic_photo_24dp)
            crossfade(true)
        }
    }

    @Deprecated("Inject StationPlaybackUseCase or MediaControllerRepository directly instead of using static Utils.")
    @JvmStatic
    fun getCurrentOrLastStation(
        ctx: Context,
        mediaControllerRepository: com.github.fschroffner.radiodroid3.player.MediaControllerRepository? = null,
        historyManager: HistoryManager? = null
    ): DataRadioStation? {
        val repo = mediaControllerRepository
            ?: dagger.hilt.EntryPoints.get(ctx.applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java).mediaControllerRepository()
        val hm = historyManager
            ?: dagger.hilt.EntryPoints.get(ctx.applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java).historyManager()
        return repo.getCurrentStation() ?: hm.getFirst()
    }

    @JvmStatic
    fun showPlayerSelectorDialog(context: Context, fragmentManager: FragmentManager, station: DataRadioStation?) {
        val oldFragment = fragmentManager.findFragmentByTag(PlayerSelectorDialog.FRAGMENT_TAG)
        if (oldFragment != null && oldFragment.isVisible) return

        val playerSelectorDialogFragment = PlayerSelectorDialog(station)
        playerSelectorDialogFragment.show(fragmentManager, PlayerSelectorDialog.FRAGMENT_TAG)
    }

    @Deprecated("Inject StationPlaybackUseCase instead of using static Utils.")
    @JvmStatic
    fun showPlaySelection(
        context: Context,
        station: DataRadioStation,
        fragmentManager: FragmentManager,
        castHandler: CastHandler? = null,
        mediaControllerRepository: com.github.fschroffner.radiodroid3.player.MediaControllerRepository? = null
    ) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val externalAvailable = sharedPref.getBoolean("play_external", false)
        val ch = castHandler
            ?: dagger.hilt.EntryPoints.get(context.applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java).castHandler()
        val castAvailable = ch.isCastSessionAvailable

        if (castAvailable && !externalAvailable) {
            val useCase = dagger.hilt.EntryPoints.get(context.applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java).stationPlaybackUseCase()
            useCase.playCast(station, context.applicationContext)
        } else if (externalAvailable) {
            showPlayerSelectorDialog(context, fragmentManager, station)
        } else {
            playAndWarnIfMetered(context, station, PlayerType.RADIODROID, Runnable {
                play(context, station, mediaControllerRepository)
            }, mediaControllerRepository)
        }
    }

    @JvmStatic
    fun playAndWarnIfMetered(
        context: Context,
        station: DataRadioStation,
        playerType: PlayerType,
        playFunc: Runnable
    ) {
        val repo = dagger.hilt.EntryPoints.get(context.applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java).mediaControllerRepository()
        playAndWarnIfMetered(context, station, playerType, playFunc, repo)
    }

    @JvmStatic
    fun playAndWarnIfMetered(
        context: Context,
        station: DataRadioStation,
        playerType: PlayerType,
        playFunc: Runnable,
        mediaControllerRepository: com.github.fschroffner.radiodroid3.player.MediaControllerRepository?
    ) {
        val repo = mediaControllerRepository
            ?: dagger.hilt.EntryPoints.get(context.applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java).mediaControllerRepository()
        playAndWarnIfMetered(context, station, playerType, playFunc) { station1, playerType1 ->
            repo.setStation(station1)
            repo.warnAboutMeteredConnection(playerType1)
        }
    }

    @JvmStatic
    fun urlIndicatesHlsStream(streamUrl: String): Boolean {
        val p = Regex(".*\\.m3u8([#?\\s].*)?$")
        return p.matches(streamUrl)
    }

    @JvmStatic
    fun resolvePlaylistIfNeeded(httpClient: OkHttpClient, streamUrl: String): String {
        val trimmed = streamUrl.trim()
        val lower = trimmed.lowercase()
        if (lower.contains(".m3u8")) return trimmed

        val isM3u = lower.endsWith(".m3u") || lower.contains(".m3u?")
        val isPls = lower.endsWith(".pls") || lower.contains(".pls?")

        if (!isM3u && !isPls) return trimmed

        try {
            val request = Request.Builder().url(trimmed).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return trimmed
                val body = response.body?.string() ?: return trimmed

                if (isPls || body.startsWith("[playlist]", ignoreCase = true)) {
                    val fileLine = body.lineSequence().firstOrNull { it.trim().startsWith("File", ignoreCase = true) && it.contains("=") }
                    if (fileLine != null) {
                        val url = fileLine.substringAfter("=").trim()
                        if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                            return url
                        }
                    }
                } else {
                    val urlLine = body.lineSequence()
                        .map { it.trim() }
                        .firstOrNull { !it.startsWith("#") && (it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)) }
                    if (urlLine != null) {
                        return urlLine
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("UTIL", "Could not resolve playlist $streamUrl: $e")
        }
        return trimmed
    }

    fun interface MeteredWarningCallback {
        fun warn(station: DataRadioStation, playerType: PlayerType)
    }

    @JvmStatic
    fun playAndWarnIfMetered(context: Context, station: DataRadioStation, playerType: PlayerType,
                             playFunc: Runnable, warningCallback: MeteredWarningCallback) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val warnOnMetered = sharedPref.getBoolean("warn_no_wifi", false)

        if (warnOnMetered && ConnectivityChecker.getCurrentConnectionType(context) == ConnectivityChecker.ConnectionType.METERED) {
            warningCallback.warn(station, playerType)
        } else {
            playFunc.run()
        }
    }


    @Deprecated("Inject MediaControllerRepository directly instead of using static Utils.")
    @JvmStatic
    fun play(
        context: Context,
        station: DataRadioStation,
        mediaControllerRepository: com.github.fschroffner.radiodroid3.player.MediaControllerRepository? = null
    ) {
        val repo = mediaControllerRepository
            ?: dagger.hilt.EntryPoints.get(context.applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java).mediaControllerRepository()
        repo.playStation(station)
    }

    @JvmStatic
    fun shouldLoadIcons(context: Context?): Boolean {
        return when (loadIcons) {
            -1 -> {
                if (PreferenceManager.getDefaultSharedPreferences(context!!.applicationContext).getBoolean("load_icons", false)) {
                    loadIcons = 1
                    true
                } else {
                    loadIcons = 0
                    true
                }
            }
            0 -> false
            1 -> true
            else -> false
        }
    }

    @JvmStatic
    fun getTheme(context: Context): String = ThemeUtils.getTheme(context)

    @JvmStatic
    fun getThemeResId(context: Context): Int = ThemeUtils.getThemeResId(context)

    @JvmStatic
    fun applyTheme(activity: Activity) = ThemeUtils.applyTheme(activity)

    @JvmStatic
    fun isDarkTheme(context: Context): Boolean = ThemeUtils.isDarkTheme(context)

    @JvmStatic
    fun getTimePickerThemeResId(context: Context): Int = ThemeUtils.getTimePickerThemeResId(context)

    @JvmStatic
    fun useCircularIcons(context: Context): Boolean {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPref.getBoolean("circular_icons", false)
    }

    private val PERMISSIONS_STORAGE = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    @JvmStatic
    fun verifyStoragePermissions(activity: Activity, request_id: Int): Boolean {
        val permission = ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, request_id)
            return false
        }
        return true
    }

    @JvmStatic
    fun verifyStoragePermissions(fragment: Fragment, request_id: Int): Boolean {
        val permission = ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            @Suppress("DEPRECATION")
            fragment.requestPermissions(PERMISSIONS_STORAGE, request_id)
            return false
        }
        return true
    }

    @JvmStatic
    fun getReadableBytes(bytes: Double): String {
        val str = arrayOf("B", "KB", "MB", "GB", "TB")
        var b = bytes
        for (aStr in str) {
            if (b < 1024) return String.format(Locale.getDefault(), "%1$,.1f %2\$s", b, aStr)
            b /= 1024
        }
        return String.format(Locale.getDefault(), "%1$,.1f %2\$s", b * 1024, str[str.size - 1])
    }

    @JvmStatic
    fun sanitizeName(str: String): String =
        str.replace(Regex("\\W+"), "_").replace(Regex("^_+"), "").replace(Regex("_+$"), "")

    @JvmStatic
    fun hasWifiConnection(context: Context): Boolean = NetworkUtils.hasWifiConnection(context)

    @JvmStatic
    fun hasAnyConnection(context: Context): Boolean = NetworkUtils.hasAnyConnection(context)

    @JvmStatic
    fun bottomNavigationEnabled(context: Context): Boolean {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPref.getBoolean("bottom_navigation", true)
    }

    @JvmStatic
    fun formatStringWithNamedArgs(format: String, args: Map<String, String>): String {
        val builder = StringBuilder(format)
        for ((key, value) in args) {
            val templateKey = "\${$key}"
            var startIdx = 0
            while (true) {
                val keyIdx = builder.indexOf(templateKey, startIdx)
                if (keyIdx == -1) break
                builder.replace(keyIdx, keyIdx + templateKey.length, value)
                startIdx = keyIdx + value.length
            }
        }
        return builder.toString()
    }

    @JvmStatic
    fun themeAttributeToColor(themeAttributeId: Int, context: Context, fallbackColorId: Int): Int =
        ThemeUtils.themeAttributeToColor(themeAttributeId, context, fallbackColorId)

    @JvmStatic
    fun getIconColor(context: Context): Int = ThemeUtils.getIconColor(context)

    @JvmStatic
    fun getAccentColor(context: Context): Int = ThemeUtils.getAccentColor(context)

    @JvmStatic
    fun setOkHttpProxy(builder: OkHttpClient.Builder, proxySettings: ProxySettings): Boolean {
        if (proxySettings.type == Proxy.Type.DIRECT) return true
        if (TextUtils.isEmpty(proxySettings.host)) return false
        if (proxySettings.port < 1 || proxySettings.port > 65535) return false

        val proxyAddress = InetSocketAddress.createUnresolved(proxySettings.host, proxySettings.port)
        val proxy = Proxy(proxySettings.type, proxyAddress)
        builder.proxy(proxy)

        if (!proxySettings.login.isNullOrEmpty()) {
            val proxyAuthenticator = Authenticator { _, response ->
                val credential = Credentials.basic(proxySettings.login.orEmpty(), proxySettings.password.orEmpty())
                response.request.newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            }
            builder.authenticator(proxyAuthenticator)
        }

        return true
    }

    @JvmStatic
    fun resourceToUri(resources: Resources, resID: Int): Uri {
        return Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                resources.getResourcePackageName(resID) + '/' +
                resources.getResourceTypeName(resID) + '/' +
                resources.getResourceEntryName(resID)
        )
    }

    @JvmStatic
    fun IconicsIcon(context: Context, icon: IIcon): IconicsDrawable {
        return IconicsDrawable(context, icon).size(IconicsSize.TOOLBAR_ICON_SIZE).padding(IconicsSize.TOOLBAR_ICON_PADDING).color(IconicsColor.colorInt(getIconColor(context)))
    }

    @JvmStatic
    fun getMimeType(url: String, defaultMimeType: String): String {
        var type = defaultMimeType
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: defaultMimeType
        }
        return type
    }

    @JvmStatic
    fun enableTls12OnPreLollipop(client: OkHttpClient.Builder): OkHttpClient.Builder {
        if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT < 22) {
            try {
                val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                trustManagerFactory.init(null as KeyStore?)
                val tmList = trustManagerFactory.trustManagers
                Log.i("OkHttpTLSCompat", "Found trustmanagers:${tmList.size}")
                val tm = tmList[0] as X509TrustManager

                val sc = SSLContext.getInstance("TLSv1.2")
                sc.init(null, null, null)
                client.sslSocketFactory(Tls12SocketFactory(sc.socketFactory), tm)

                val cs = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2)
                    .build()

                val specs = listOf(cs, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT)
                client.connectionSpecs(specs)
            } catch (exc: Exception) {
                Log.e("OkHttpTLSCompat", "Error while setting TLS 1.2", exc)
            }
        }
        return client
    }
}
