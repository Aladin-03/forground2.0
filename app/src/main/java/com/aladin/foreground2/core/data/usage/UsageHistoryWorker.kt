package com.aladin.foreground2.core.data.usage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aladin.foreground2.core.logging.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Daily fallback for the history snapshot, for days the detection service missed (e.g. it wasn't running at
 * midnight). Rewrites yesterday and fills any gaps; idempotent like every catch-up.
 */
class UsageHistoryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HistoryEntryPoint {
        fun usageHistory(): UsageHistory
    }

    override suspend fun doWork(): Result {
        val history =
            EntryPointAccessors.fromApplication(applicationContext, HistoryEntryPoint::class.java).usageHistory()
        history.catchUp(HistoryTrigger.Worker, rewriteYesterday = true)
        return Result.success()
    }

    companion object {
        private const val NAME = "usage-history"

        /** Schedules the daily job once; later calls keep the existing schedule. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageHistoryWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            Log.d(Log.Area.Stats) { "history worker scheduled" }
        }
    }
}
