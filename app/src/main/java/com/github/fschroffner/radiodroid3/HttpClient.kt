package com.github.fschroffner.radiodroid3

import okhttp3.OkHttpClient

object HttpClient {
    @JvmStatic
    val instance: OkHttpClient = OkHttpClient()
}
