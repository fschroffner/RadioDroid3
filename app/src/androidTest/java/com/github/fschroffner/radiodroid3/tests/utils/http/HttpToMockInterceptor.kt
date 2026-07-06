package com.github.fschroffner.radiodroid3.tests.utils.http

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.mockwebserver.MockWebServer
import java.net.InetSocketAddress

class HttpToMockInterceptor(mockWebServer: MockWebServer) : Interceptor {

    private val address: InetSocketAddress = InetSocketAddress(mockWebServer.hostName, mockWebServer.port)

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        val httpUrl = HttpUrl.Builder().scheme("http")
            .host(address.hostName)
            .port(address.port)
            .addQueryParameter("url", request.url.toString())
            .build()

        request = request.newBuilder()
            .url(httpUrl)
            .build()

        return chain.proceed(request)
    }
}
