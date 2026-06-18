package net.programmierecke.radiodroid2.proxy

import android.content.SharedPreferences
import com.google.gson.Gson
import java.net.Proxy

class ProxySettings {
    var host: String? = null
    var port: Int = 0
    var login: String? = null
    var password: String? = null
    var type: Proxy.Type? = null

    fun toPreferences(sharedPrefEditor: SharedPreferences.Editor) {
        sharedPrefEditor.putString(PREFERENCES_KEY, Gson().toJson(this))
    }

    companion object {
        private const val PREFERENCES_KEY = "proxySettings"

        @JvmStatic
        fun fromPreferences(sharedPref: SharedPreferences): ProxySettings? =
            Gson().fromJson(sharedPref.getString(PREFERENCES_KEY, ""), ProxySettings::class.java)
    }
}
