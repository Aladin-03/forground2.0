package com.aladin.foreground2.di

import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock

    @Provides
    @Singleton
    fun provideTiming(): Timing = Timing.fromBuildConfig()
}
