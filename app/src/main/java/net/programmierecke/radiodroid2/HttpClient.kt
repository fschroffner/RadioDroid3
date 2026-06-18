package net.programmierecke.radiodroid2

import okhttp3.OkHttpClient

object HttpClient {
    @JvmStatic
    val instance: OkHttpClient = OkHttpClient()
}
