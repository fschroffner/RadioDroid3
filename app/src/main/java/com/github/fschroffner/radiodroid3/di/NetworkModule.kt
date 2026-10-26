package com.github.fschroffner.radiodroid3.di

import android.content.Context
import android.text.TextUtils
import com.github.fschroffner.radiodroid3.BuildConfig
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.data.datastore.AppPreferencesDataStore
import com.github.fschroffner.radiodroid3.proxy.ProxySettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Volatile
    var testsInterceptor: Interceptor? = null

    class UserAgentInterceptor(private val userAgent: String) : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val requestWithUserAgent = chain.request().newBuilder().header("User-Agent", userAgent).build()
            return chain.proceed(requestWithUserAgent)
        }
    }

    @Provides
    @Singleton
    fun provideConnectionPool(): ConnectionPool = ConnectionPool()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        connectionPool: ConnectionPool,
        appPreferencesDataStore: AppPreferencesDataStore
    ): OkHttpClient {
        val dynamicProxySelector = object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                val proxySettings = appPreferencesDataStore.getProxySettings()
                if (proxySettings != null && proxySettings.type != Proxy.Type.DIRECT &&
                    !TextUtils.isEmpty(proxySettings.host) && proxySettings.port in 1..65535
                ) {
                    val proxyAddress = InetSocketAddress.createUnresolved(proxySettings.host, proxySettings.port)
                    return listOf(Proxy(proxySettings.type, proxyAddress))
                }
                return listOf(Proxy.NO_PROXY)
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                // Ignore connection failure
            }
        }

        val dynamicProxyAuthenticator = Authenticator { _, response ->
            val proxySettings = appPreferencesDataStore.getProxySettings()
            if (proxySettings != null && !proxySettings.login.isNullOrEmpty()) {
                val credential = Credentials.basic(proxySettings.login.orEmpty(), proxySettings.password.orEmpty())
                response.request.newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            } else null
        }

        val builder = OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor("RadioDroid2/${BuildConfig.VERSION_NAME}"))
            .proxySelector(dynamicProxySelector)
            .proxyAuthenticator(dynamicProxyAuthenticator)

        builder.addInterceptor { chain ->
            testsInterceptor?.intercept(chain) ?: chain.proceed(chain.request())
        }

        return Utils.enableTls12OnPreLollipop(builder).build()
    }
}
