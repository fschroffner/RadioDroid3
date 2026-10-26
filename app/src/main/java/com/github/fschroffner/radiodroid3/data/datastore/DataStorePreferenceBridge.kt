package com.github.fschroffner.radiodroid3.data.datastore

import androidx.preference.PreferenceDataStore

class DataStorePreferenceBridge(
    private val dataStore: AppPreferencesDataStore
) : PreferenceDataStore() {

    override fun putString(key: String, value: String?) {
        dataStore.putStringAsync(key, value)
    }

    override fun getString(key: String, defValue: String?): String? {
        return dataStore.getString(key, defValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        dataStore.putBooleanAsync(key, value)
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return dataStore.getBoolean(key, defValue)
    }

    override fun putInt(key: String, value: Int) {
        dataStore.putIntAsync(key, value)
    }

    override fun getInt(key: String, defValue: Int): Int {
        return dataStore.getInt(key, defValue)
    }

    override fun putLong(key: String, value: Long) {
        dataStore.putLongAsync(key, value)
    }

    override fun getLong(key: String, defValue: Long): Long {
        return dataStore.getLong(key, defValue)
    }
}
