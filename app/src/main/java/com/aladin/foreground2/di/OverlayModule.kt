package com.aladin.foreground2.di

import com.aladin.foreground2.service.overlay.AudioManagerMediaControls
import com.aladin.foreground2.service.overlay.HomeLauncher
import com.aladin.foreground2.service.overlay.IntentHomeLauncher
import com.aladin.foreground2.service.overlay.KeyEventMediaPauser
import com.aladin.foreground2.service.overlay.MediaControls
import com.aladin.foreground2.service.overlay.MediaPauser
import com.aladin.foreground2.service.overlay.MuteRecord
import com.aladin.foreground2.service.overlay.OverlayHost
import com.aladin.foreground2.service.overlay.SettingsMuteRecord
import com.aladin.foreground2.service.overlay.UsagePill
import com.aladin.foreground2.service.overlay.WindowOverlayHost
import com.aladin.foreground2.service.overlay.WindowUsagePill
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
    fun bindUsagePill(impl: WindowUsagePill): UsagePill

    @Binds
    fun bindHomeLauncher(impl: IntentHomeLauncher): HomeLauncher

    @Binds
    fun bindMediaPauser(impl: KeyEventMediaPauser): MediaPauser

    @Binds
    fun bindMediaControls(impl: AudioManagerMediaControls): MediaControls

    @Binds
    fun bindMuteRecord(impl: SettingsMuteRecord): MuteRecord
}
