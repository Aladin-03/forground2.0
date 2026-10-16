package com.aladin.foreground2.di

import com.aladin.foreground2.service.overlay.HomeLauncher
import com.aladin.foreground2.service.overlay.IntentHomeLauncher
import com.aladin.foreground2.service.overlay.OverlayHost
import com.aladin.foreground2.service.overlay.WindowOverlayHost
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface OverlayModule {
    @Binds
    fun bindOverlayHost(impl: WindowOverlayHost): OverlayHost

    @Binds
    fun bindHomeLauncher(impl: IntentHomeLauncher): HomeLauncher
}
