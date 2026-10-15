package com.aladin.foreground2

import android.app.Application
import com.aladin.foreground2.core.logging.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ForegroundApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(Log.Area.App) { "onCreate ${BuildConfig.BUILD_TYPE} ${BuildConfig.VERSION_NAME}" }
    }
}
