package com.aladin.foreground2.di

import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.data.apps.PackageManagerLauncherAppSource
import com.aladin.foreground2.core.data.repo.RoomWatchedAppsRepository
import com.aladin.foreground2.core.data.repo.WatchedAppsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface RepoModule {
    @Binds
    fun bindWatchedAppsRepository(impl: RoomWatchedAppsRepository): WatchedAppsRepository

    @Binds
    fun bindLauncherAppSource(impl: PackageManagerLauncherAppSource): LauncherAppSource
}
