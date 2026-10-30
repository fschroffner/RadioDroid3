package com.github.fschroffner.radiodroid3

import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
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
import com.github.fschroffner.radiodroid3.players.PlayStationTask
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
        val chaine = StringBuilder("")
        return try {
            var aFileName = theURI.lowercase().replace("http://", "")
            aFileName = aFileName.lowercase().replace("https://", "")
            aFileName = sanitizeName(aFileName)

            val file = File(ctx.cacheDir.absolutePath + "/" + aFileName)
            val lastModDate = Date(file.lastModified())
            val now = Date()
            val millis = now.time - file.lastModified()
            val secs = millis / 1000
            val mins = secs / 60
            val hours = mins / 60

            if (BuildConfig.DEBUG) {
                Log.d("UTIL", "File last modified : $lastModDate secs=$secs  mins=$mins hours=$hours")
            }

            if (hours < 1) {
                val aStream = FileInputStream(file)
                val rd = BufferedReader(InputStreamReader(aStream))
                var line: String?
                while (rd.readLine().also { line = it } != null) {
                    chaine.append(line)
                }
                rd.close()
                if (BuildConfig.DEBUG) Log.d("UTIL", "used cache for:$theURI")
                return chaine.toString()
            }
            if (BuildConfig.DEBUG) Log.d("UTIL", "do not use cache, because too old:$theURI")
            null
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

            val f = File(ctx.cacheDir.toString() + "/" + aFileName)
            val aStream = FileOutputStream(f)
            aStream.write(content.toByteArray(Charsets.UTF_8))
            aStream.close()
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
            val response = httpClient.newCall(request).execute()
            val responseStr = response.body!!.string()

            if (!response.isSuccessful) {
                Log.e("UTIL", "Unsuccessful response: ${response.message}\n$responseStr")
                return null
            }

            writeFileCache(ctx, theURI, responseStr)
            if (BuildConfig.DEBUG) Log.d("UTIL", "wrote cache file for:$theURI")
            responseStr
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
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 70f, holder.context.resources.displayMetrics)
        val placeholder = AppCompatResources.getDrawable(holder.context, R.drawable.ic_photo_24dp)
        Picasso.get().load(fromUrl).placeholder(placeholder!!).resize(px.toInt(), 0)
            .networkPolicy(NetworkPolicy.OFFLINE)
            .into(holder, object : Callback {
                override fun onSuccess() {}
                override fun onError(e: Exception) {
                    Picasso.get().load(fromUrl).placeholder(placeholder).resize(px.toInt(), 0)
                        .networkPolicy(NetworkPolicy.NO_CACHE).into(holder)
                }
            })
    }

    @JvmStatic
    fun getCurrentOrLastStation(ctx: Context): DataRadioStation? {
        val entryPoint = dagger.hilt.EntryPoints.get(ctx.applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java)
        var station = entryPoint.mediaControllerRepository().getCurrentStation()
        if (station == null) {
            station = entryPoint.historyManager().getFirst()
        }
        return station
    }

    @JvmStatic
    fun showMpdServersDialog(context: Context, fragmentManager: FragmentManager, station: DataRadioStation?) {
        val oldFragment = fragmentManager.findFragmentByTag(PlayerSelectorDialog.FRAGMENT_TAG)
        if (oldFragment != null && oldFragment.isVisible) return

        val entryPoint = dagger.hilt.EntryPoints.get(context.applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java)
        val playerSelectorDialogFragment = PlayerSelectorDialog(entryPoint.mpdClient(), station)
        playerSelectorDialogFragment.show(fragmentManager, PlayerSelectorDialog.FRAGMENT_TAG)
    }

    @JvmStatic
    fun showPlaySelection(context: Context, station: DataRadioStation, fragmentManager: FragmentManager) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val externalAvailable = sharedPref.getBoolean("play_external", false)
        val entryPoint = dagger.hilt.EntryPoints.get(context.applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java)
        val castHandler = entryPoint.castHandler()
        val castAvailable = castHandler.isCastSessionAvailable
        val mpdAvailable = entryPoint.mpdClient().isMpdEnabled

        if (castAvailable && !externalAvailable && !mpdAvailable) {
            PlayStationTask(station, context.applicationContext,
                { url -> castHandler.playRemote(station.Name, url, station.IconUrl) },
                null).execute()
        } else if (externalAvailable || mpdAvailable) {
            showMpdServersDialog(context, fragmentManager, station)
        } else {
            playAndWarnIfMetered(context, station, PlayerType.RADIODROID) { play(station) }
        }
    }

    @JvmStatic
    fun playAndWarnIfMetered(context: Context, station: DataRadioStation, playerType: PlayerType, playFunc: Runnable) {
        val entryPoint = dagger.hilt.EntryPoints.get(context.applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java)
        playAndWarnIfMetered(context, station, playerType, playFunc) { station1, playerType1 ->
            entryPoint.mediaControllerRepository().setStation(station1)
            entryPoint.mediaControllerRepository().warnAboutMeteredConnection(playerType1)
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
            val response = httpClient.newCall(request).execute()
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

    @JvmStatic
    fun play(station: DataRadioStation) {
        RadioDroidApp.instance?.let { play(it, station) }
    }

    @JvmStatic
    fun play(context: Context, station: DataRadioStation) {
        val entryPoint = dagger.hilt.EntryPoints.get(context.applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java)
        entryPoint.mediaControllerRepository().playStation(station)
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
    fun getTheme(context: Context): String {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val spTheme = sharedPref.getString("theme_name", null)
        if (!spTheme.isNullOrBlank()) {
            return spTheme
        }
        try {
            val entryPoint = dagger.hilt.EntryPoints.get(
                context.applicationContext,
                com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java
            )
            val dsTheme = entryPoint.appPreferencesDataStore().getString("theme_name", null)
            if (!dsTheme.isNullOrBlank()) {
                sharedPref.edit().putString("theme_name", dsTheme).commit()
                return dsTheme
            }
        } catch (_: Exception) {}
        return context.resources.getString(R.string.theme_light)
    }

    private fun isSystemDarkMode(context: Context): Boolean {
        val nightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    @JvmStatic
    fun getThemeResId(context: Context): Int {
        val theme = getTheme(context)
        val themeValues = try { context.resources.getStringArray(R.array.theme_values) } catch (_: Exception) { emptyArray() }
        val themeEntries = try { context.resources.getStringArray(R.array.theme_entries) } catch (_: Exception) { emptyArray() }

        val darkVal = if (themeValues.size > 1) themeValues[1] else ""
        val darkEntry = if (themeEntries.size > 1) themeEntries[1] else ""
        val oledVal = if (themeValues.size > 2) themeValues[2] else ""
        val oledEntry = if (themeEntries.size > 2) themeEntries[2] else ""
        val sysVal = if (themeValues.size > 3) themeValues[3] else ""
        val sysEntry = if (themeEntries.size > 3) themeEntries[3] else ""

        return when {
            theme.equals("dark", ignoreCase = true) ||
            theme.equals("dunkel", ignoreCase = true) ||
            theme.equals(darkVal, ignoreCase = true) ||
            theme.equals(darkEntry, ignoreCase = true) ||
            theme.equals(context.resources.getString(R.string.theme_dark), ignoreCase = true) -> R.style.MyMaterialTheme_Dark

            theme.equals("oled", ignoreCase = true) ||
            theme.contains("oled", ignoreCase = true) ||
            theme.equals(oledVal, ignoreCase = true) ||
            theme.equals(oledEntry, ignoreCase = true) ||
            theme.equals(context.resources.getString(R.string.theme_oled), ignoreCase = true) -> R.style.MyMaterialTheme_Oled

            theme.equals("system", ignoreCase = true) ||
            theme.startsWith("system", ignoreCase = true) ||
            theme.equals(sysVal, ignoreCase = true) ||
            theme.equals(sysEntry, ignoreCase = true) ||
            theme.equals(context.resources.getString(R.string.theme_system), ignoreCase = true) ->
                if (isSystemDarkMode(context)) R.style.MyMaterialTheme_Dark else R.style.MyMaterialTheme

            else -> R.style.MyMaterialTheme
        }
    }

    @JvmStatic
    fun applyTheme(activity: Activity) {
        val themeResId = getThemeResId(activity)
        activity.setTheme(themeResId)

        val nightMode = when (themeResId) {
            R.style.MyMaterialTheme_Dark, R.style.MyMaterialTheme_Oled -> AppCompatDelegate.MODE_NIGHT_YES
            else -> {
                val theme = getTheme(activity)
                if (theme.contains("system", ignoreCase = true)) {
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                }
            }
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    @JvmStatic
    fun isDarkTheme(context: Context): Boolean {
        val themeId = getThemeResId(context)
        return themeId == R.style.MyMaterialTheme_Dark || themeId == R.style.MyMaterialTheme_Oled
    }

    @JvmStatic
    fun getTimePickerThemeResId(context: Context): Int {
        return when (getThemeResId(context)) {
            R.style.MyMaterialTheme_Dark -> R.style.DialogTheme_Dark
            R.style.MyMaterialTheme_Oled -> R.style.DialogTheme_Oled
            else -> R.style.DialogTheme
        }
    }

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
    @Suppress("DEPRECATION")
    fun hasWifiConnection(context: Context): Boolean {
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        return mWifi!!.isConnected
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun hasAnyConnection(context: Context): Boolean {
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netInfo = connManager.activeNetworkInfo
        return netInfo != null && netInfo.isConnected
    }

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
    fun themeAttributeToColor(themeAttributeId: Int, context: Context, fallbackColorId: Int): Int {
        val outValue = TypedValue()
        val theme = context.theme
        val wasResolved = theme.resolveAttribute(themeAttributeId, outValue, true)
        return if (wasResolved) {
            if (outValue.resourceId == 0) outValue.data else ContextCompat.getColor(context, outValue.resourceId)
        } else {
            fallbackColorId
        }
    }

    @JvmStatic
    fun getIconColor(context: Context): Int = themeAttributeToColor(R.attr.menuTextColorDefault, context, Color.LTGRAY)

    @JvmStatic
    fun getAccentColor(context: Context): Int = themeAttributeToColor(R.attr.colorAccent, context, Color.LTGRAY)

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
