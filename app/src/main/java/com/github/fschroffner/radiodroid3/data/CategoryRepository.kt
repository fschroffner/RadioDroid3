package com.github.fschroffner.radiodroid3.data

import android.content.Context
import com.github.fschroffner.radiodroid3.Utils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository providing access to category endpoints (tags, countries, languages).
 */
@Singleton
class CategoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {
    suspend fun getCategories(relativeUrl: String, forceUpdate: Boolean): List<DataCategory>? =
        withContext(Dispatchers.IO) {
            val json = Utils.downloadFeedRelative(httpClient, context, relativeUrl, forceUpdate, null)
                ?: return@withContext null
            DataCategory.DecodeJson(json).toList()
        }
}
