package com.github.fschroffner.radiodroid3.di

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.github.fschroffner.radiodroid3.RadioDroidApp
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Bridges existing manually-wired singletons (created in [RadioDroidApp.onCreate])
 * into the Hilt dependency graph so new components can request them via constructor
 * injection instead of casting `application as RadioDroidApp`.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(application: Application): OkHttpClient =
        (application as RadioDroidApp).httpClient
}
