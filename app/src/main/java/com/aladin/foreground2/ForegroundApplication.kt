package com.aladin.foreground2

import android.app.Application
import com.aladin.foreground2.core.data.usage.HistoryTrigger
import com.aladin.foreground2.core.data.usage.UsageHistory
import com.aladin.foreground2.core.data.usage.UsageHistoryWorker
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ForegroundApplication : Application() {
    @Inject lateinit var usageHistory: UsageHistory

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        Log.i(Log.Area.App) { "onCreate ${BuildConfig.BUILD_TYPE} ${BuildConfig.VERSION_NAME}" }
        // Saves finished days into the history (backfilling on the first launch after an update), with a daily
        // job as a fallback for days nothing else caught.
        UsageHistoryWorker.schedule(this)
        scope.launch { usageHistory.catchUp(HistoryTrigger.AppStart) }
    }
}
