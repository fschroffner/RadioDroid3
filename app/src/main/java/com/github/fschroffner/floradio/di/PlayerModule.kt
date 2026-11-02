package com.github.fschroffner.floradio.di

import com.github.fschroffner.floradio.player.MediaControllerRepository
import com.github.fschroffner.floradio.player.RadioPlayerController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    @Singleton
    abstract fun bindRadioPlayerController(
        mediaControllerRepository: MediaControllerRepository
    ): RadioPlayerController
}
