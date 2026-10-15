package com.aladin.foreground2.di

import android.content.Context
import com.aladin.foreground2.service.detection.AndroidDetectionServiceController
import com.aladin.foreground2.service.detection.DetectionServiceController
import com.aladin.foreground2.service.detection.UsageEventSource
import com.aladin.foreground2.service.detection.UsageStatsEventSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

/** This app's own package name. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OwnPackageName

@Module
@InstallIn(SingletonComponent::class)
interface DetectionModule {
    @Binds
    fun bindUsageEventSource(impl: UsageStatsEventSource): UsageEventSource

    @Binds
    fun bindDetectionServiceController(impl: AndroidDetectionServiceController): DetectionServiceController

    companion object {
        @Provides
        @OwnPackageName
        fun provideOwnPackageName(
            @ApplicationContext context: Context,
        ): String = context.packageName
    }
}
