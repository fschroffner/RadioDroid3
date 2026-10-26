package com.github.fschroffner.radiodroid3.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.fschroffner.radiodroid3.proxy.ProxySettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "radiodroid_preferences",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(
                context = context,
                sharedPreferencesName = context.packageName + "_preferences"
            )
        )
    }
)

@Singleton
class AppPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val dataStore: DataStore<Preferences> = context.preferencesDataStore

    @Volatile
    private var inMemoryCache: Preferences = emptyPreferences()

    init {
        scope.launch {
            dataStore.data
                .catch { exception ->
                    if (exception is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }
                .collect { prefs ->
                    inMemoryCache = prefs
                }
        }
    }

    object Keys {
        val THEME_NAME = stringPreferencesKey("theme_name")
        val CIRCULAR_ICONS = booleanPreferencesKey("circular_icons")
        val COMPACT_STYLE = booleanPreferencesKey("compact_style")
        val BOTTOM_NAVIGATION = booleanPreferencesKey("bottom_navigation")
        val STARTUP_ACTION = stringPreferencesKey("startup_action")
        val AUTO_PLAY_ON_STARTUP = booleanPreferencesKey("auto_play_on_startup")
        val AUTO_OFF_ON_STARTUP = booleanPreferencesKey("auto_off_on_startup")
        val AUTO_OFF_TIMEOUT = stringPreferencesKey("auto_off_timeout")
        val LOAD_ICONS = booleanPreferencesKey("load_icons")
        val ICON_CLICK_TOGGLES_FAVORITE = booleanPreferencesKey("icon_click_toggles_favorite")
        val AUTO_FAVORITE = booleanPreferencesKey("auto_favorite")
        val SHOW_BROKEN = booleanPreferencesKey("show_broken")
        val CLICK_TREND_ICON_VISIBLE = booleanPreferencesKey("click_trend_icon_visible")
        val SINGLE_USE_TAGS = booleanPreferencesKey("single_use_tags")
        val PLAY_EXTERNAL = booleanPreferencesKey("play_external")
        val WARN_NO_WIFI = booleanPreferencesKey("warn_no_wifi")
        val PAUSE_WHEN_NOISY = booleanPreferencesKey("pause_when_noisy")
        val AUTO_RESUME_WIRED_HEADSET = booleanPreferencesKey("auto_resume_on_wired_headset_connection")
        val AUTO_RESUME_BLUETOOTH = booleanPreferencesKey("auto_resume_on_bluetooth_a2dp_connection")
        val ALARM_TIMEOUT = stringPreferencesKey("alarm_timeout")
        val RECORD_NAME_FORMATTING = stringPreferencesKey("record_name_formatting")
        val STREAM_CONNECT_TIMEOUT = intPreferencesKey("stream_connect_timeout")
        val STREAM_READ_TIMEOUT = intPreferencesKey("stream_read_timeout")
        val SETTINGS_RETRY_TIMEOUT = intPreferencesKey("settings_retry_timeout")
        val SETTINGS_RETRY_DELAY = intPreferencesKey("settings_retry_delay")
        val SETTINGS_RESUME_WITHIN = intPreferencesKey("settings_resume_within")
        val ICONS_ONLY_FAVORITES_STYLE = booleanPreferencesKey("icons_only_favorites_style")
        val SLEEP_TIMER_DEFAULT_MINUTES = intPreferencesKey("sleep_timer_default_minutes")
        val LAST_MENU_ITEM = intPreferencesKey("LAST_MENU_ITEM")
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_TYPE = stringPreferencesKey("proxy_type")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val PROXY_LOGIN = stringPreferencesKey("proxy_login")
        val PROXY_PASSWORD = stringPreferencesKey("proxy_password")
        val RADIO_NET_DEFAULT_SERVER_ID = stringPreferencesKey("radio_net_default_server_id")
    }

    val preferencesFlow: Flow<Preferences> = dataStore.data.catch { exception ->
        if (exception is IOException) {
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    val themeFlow: Flow<String> = preferencesFlow.map { it[Keys.THEME_NAME] ?: "light" }
    val circularIconsFlow: Flow<Boolean> = preferencesFlow.map { it[Keys.CIRCULAR_ICONS] ?: false }
    val compactStyleFlow: Flow<Boolean> = preferencesFlow.map { it[Keys.COMPACT_STYLE] ?: false }
    val bottomNavigationFlow: Flow<Boolean> = preferencesFlow.map { it[Keys.BOTTOM_NAVIGATION] ?: true }
    val loadIconsFlow: Flow<Boolean> = preferencesFlow.map { it[Keys.LOAD_ICONS] ?: true }
    val iconsOnlyFavoritesStyleFlow: Flow<Boolean> = preferencesFlow.map { it[Keys.ICONS_ONLY_FAVORITES_STYLE] ?: false }
    val warnNoWifiFlow: Flow<Boolean> = preferencesFlow.map { it[Keys.WARN_NO_WIFI] ?: false }
    val pauseWhenNoisyFlow: Flow<Boolean> = preferencesFlow.map { it[Keys.PAUSE_WHEN_NOISY] ?: true }
    val autoResumeHeadsetFlow: Flow<Boolean> = preferencesFlow.map { it[Keys.AUTO_RESUME_WIRED_HEADSET] ?: false }
    val autoResumeBluetoothFlow: Flow<Boolean> = preferencesFlow.map { it[Keys.AUTO_RESUME_BLUETOOTH] ?: false }
    val sleepTimerDefaultMinutesFlow: Flow<Int> = preferencesFlow.map { it[Keys.SLEEP_TIMER_DEFAULT_MINUTES] ?: 10 }
    val lastMenuItemFlow: Flow<Int> = preferencesFlow.map { it[Keys.LAST_MENU_ITEM] ?: -1 }

    val proxySettingsFlow: Flow<ProxySettings?> = preferencesFlow.map { prefs ->
        val json = prefs[stringPreferencesKey("proxySettings")]
        if (!json.isNullOrBlank()) {
            try {
                return@map com.google.gson.Gson().fromJson(json, ProxySettings::class.java)
            } catch (e: Exception) {
                // fall through
            }
        }
        val enabled = prefs[Keys.PROXY_ENABLED] ?: false
        if (!enabled) null
        else {
            val typeStr = prefs[Keys.PROXY_TYPE] ?: "HTTP"
            val host = prefs[Keys.PROXY_HOST] ?: ""
            val port = prefs[Keys.PROXY_PORT] ?: 8080
            val login = prefs[Keys.PROXY_LOGIN]
            val password = prefs[Keys.PROXY_PASSWORD]
            ProxySettings().apply {
                this.host = host
                this.port = port
                this.login = login
                this.password = password
                this.type = try { java.net.Proxy.Type.valueOf(typeStr) } catch (e: Exception) { java.net.Proxy.Type.HTTP }
            }
        }
    }

    fun getProxySettings(): ProxySettings? {
        val json = getString("proxySettings", null)
        if (!json.isNullOrBlank()) {
            try {
                return com.google.gson.Gson().fromJson(json, ProxySettings::class.java)
            } catch (e: Exception) {
                // fall through
            }
        }
        val enabled = getBoolean("proxy_enabled", false)
        if (!enabled) return null
        return ProxySettings().apply {
            this.host = getString("proxy_host", "")
            this.port = getInt("proxy_port", 8080)
            this.login = getString("proxy_login", null)
            this.password = getString("proxy_password", null)
            val typeStr = getString("proxy_type", "HTTP")
            this.type = try { java.net.Proxy.Type.valueOf(typeStr ?: "HTTP") } catch (e: Exception) { java.net.Proxy.Type.HTTP }
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val prefKey = booleanPreferencesKey(key)
        return inMemoryCache[prefKey] ?: defaultValue
    }

    fun getString(key: String, defaultValue: String?): String? {
        val prefKey = stringPreferencesKey(key)
        return inMemoryCache[prefKey] ?: defaultValue
    }

    fun getInt(key: String, defaultValue: Int): Int {
        val prefKey = intPreferencesKey(key)
        return inMemoryCache[prefKey] ?: defaultValue
    }

    fun getLong(key: String, defaultValue: Long): Long {
        val prefKey = longPreferencesKey(key)
        return inMemoryCache[prefKey] ?: defaultValue
    }

    suspend fun <T> setPreference(key: Preferences.Key<T>, value: T) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    suspend fun <T> removePreference(key: Preferences.Key<T>) {
        dataStore.edit { preferences ->
            preferences.remove(key)
        }
    }

    suspend fun setTheme(theme: String) = setPreference(Keys.THEME_NAME, theme)
    suspend fun setLoadIcons(enabled: Boolean) = setPreference(Keys.LOAD_ICONS, enabled)
    suspend fun setIconsOnlyFavoritesStyle(enabled: Boolean) = setPreference(Keys.ICONS_ONLY_FAVORITES_STYLE, enabled)
    suspend fun setSleepTimerDefaultMinutes(minutes: Int) = setPreference(Keys.SLEEP_TIMER_DEFAULT_MINUTES, minutes)
    suspend fun setLastMenuItem(item: Int) = setPreference(Keys.LAST_MENU_ITEM, item)

    fun putBooleanAsync(key: String, value: Boolean) {
        scope.launch { setPreference(booleanPreferencesKey(key), value) }
    }

    fun putStringAsync(key: String, value: String?) {
        scope.launch {
            val prefKey = stringPreferencesKey(key)
            if (value == null) removePreference(prefKey) else setPreference(prefKey, value)
        }
    }

    fun putIntAsync(key: String, value: Int) {
        scope.launch { setPreference(intPreferencesKey(key), value) }
    }

    fun putLongAsync(key: String, value: Long) {
        scope.launch { setPreference(longPreferencesKey(key), value) }
    }
}
