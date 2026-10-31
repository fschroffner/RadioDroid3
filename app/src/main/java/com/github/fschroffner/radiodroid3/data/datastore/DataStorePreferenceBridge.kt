package com.github.fschroffner.radiodroid3.data.datastore

import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore

/**
 * PreferenceDataStore bridge delegating to AppPreferencesDataStore as the Single Source of Truth (SSOT).
 * Keeps SharedPreferences in sync for backward compatibility with components still reading SharedPreferences directly.
 */
class DataStorePreferenceBridge(
    private val dataStore: AppPreferencesDataStore,
    private val sharedPreferences: SharedPreferences? = null
) : PreferenceDataStore() {

    override fun putString(key: String, value: String?) {
        dataStore.putStringAsync(key, value)
        sharedPreferences?.edit()?.apply {
            if (value == null) remove(key) else putString(key, value)
            apply()
        }
    }

    override fun getString(key: String, defValue: String?): String? {
        return dataStore.getString(key, defValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        dataStore.putBooleanAsync(key, value)
        sharedPreferences?.edit()?.putBoolean(key, value)?.apply()
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return dataStore.getBoolean(key, defValue)
    }

    override fun putInt(key: String, value: Int) {
        dataStore.putIntAsync(key, value)
        sharedPreferences?.edit()?.putInt(key, value)?.apply()
    }

    override fun getInt(key: String, defValue: Int): Int {
        return dataStore.getInt(key, defValue)
    }

    override fun putLong(key: String, value: Long) {
        dataStore.putLongAsync(key, value)
        sharedPreferences?.edit()?.putLong(key, value)?.apply()
    }

    override fun getLong(key: String, defValue: Long): Long {
        return dataStore.getLong(key, defValue)
    }
}
