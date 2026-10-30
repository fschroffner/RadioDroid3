package com.github.fschroffner.radiodroid3.data.datastore

import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore

class DataStorePreferenceBridge(
    private val dataStore: AppPreferencesDataStore,
    private val sharedPreferences: SharedPreferences
) : PreferenceDataStore() {

    override fun putString(key: String, value: String?) {
        dataStore.putStringAsync(key, value)
        sharedPreferences.edit().apply {
            if (value == null) {
                remove(key)
            } else {
                putString(key, value)
            }
            commit()
        }
    }

    override fun getString(key: String, defValue: String?): String? {
        val dsValue = dataStore.getString(key, null)
        if (dsValue != null) return dsValue
        return sharedPreferences.getString(key, defValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        dataStore.putBooleanAsync(key, value)
        sharedPreferences.edit().putBoolean(key, value).commit()
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        if (sharedPreferences.contains(key)) {
            return sharedPreferences.getBoolean(key, defValue)
        }
        return dataStore.getBoolean(key, defValue)
    }

    override fun putInt(key: String, value: Int) {
        dataStore.putIntAsync(key, value)
        sharedPreferences.edit().putInt(key, value).commit()
    }

    override fun getInt(key: String, defValue: Int): Int {
        if (sharedPreferences.contains(key)) {
            return sharedPreferences.getInt(key, defValue)
        }
        return dataStore.getInt(key, defValue)
    }

    override fun putLong(key: String, value: Long) {
        dataStore.putLongAsync(key, value)
        sharedPreferences.edit().putLong(key, value).commit()
    }

    override fun getLong(key: String, defValue: Long): Long {
        if (sharedPreferences.contains(key)) {
            return sharedPreferences.getLong(key, defValue)
        }
        return dataStore.getLong(key, defValue)
    }
}
