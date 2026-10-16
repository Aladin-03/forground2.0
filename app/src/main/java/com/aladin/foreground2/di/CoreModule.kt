package com.aladin.foreground2.di

import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.SystemClock
import com.aladin.foreground2.core.domain.ZoneProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.ZoneId
import javax.inject.Qualifier
import javax.inject.Singleton

/** A scope living as long as the process, for work that must outlast the caller (e.g. a write started on hide). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock

    /** Read on every call, so a time-zone change applies straight away. */
    @Provides
    fun provideZoneProvider(): ZoneProvider = ZoneProvider { ZoneId.systemDefault() }

    @Provides
    @Singleton
    fun provideTiming(): Timing = Timing.fromBuildConfig()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
