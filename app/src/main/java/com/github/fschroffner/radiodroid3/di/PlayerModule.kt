package com.github.fschroffner.radiodroid3.di

import com.github.fschroffner.radiodroid3.player.Media3PlayerController
import com.github.fschroffner.radiodroid3.player.RadioPlayerController
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
        media3Controller: Media3PlayerController
    ): RadioPlayerController
}
