package com.aladin.foreground2.core.data.repo

import com.aladin.foreground2.core.data.db.WatchedAppDao
import com.aladin.foreground2.core.data.db.WatchedAppEntity
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.watch.WatchMode
import com.aladin.foreground2.core.domain.watch.WatchedApp
import com.aladin.foreground2.core.logging.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The apps the user chose to watch, each with its mode (spec rule 1, "Watch modes"). Plain storage: the rules for
 * when a mode may change live in WatchRules, applied by [WatchChanges].
 */
interface WatchedAppsRepository {
    /** Every watched app, re-emitted on each change (including a mode change). */
    fun observeWatched(): Flow<List<WatchedApp>>

    suspend fun watch(
        app: AppInfo,
        mode: WatchMode,
    )

    suspend fun setMode(
        packageName: String,
        mode: WatchMode,
    )

    suspend fun unwatch(packageName: String)
}

@Singleton
class RoomWatchedAppsRepository
    @Inject
    constructor(
        private val dao: WatchedAppDao,
    ) : WatchedAppsRepository {
        override fun observeWatched(): Flow<List<WatchedApp>> =
            dao.observeAll().map { rows ->
                rows.map { WatchedApp(it.packageName, it.label, WatchMode.fromKey(it.mode) ?: WatchMode.DEFAULT) }
            }

        override suspend fun watch(
            app: AppInfo,
            mode: WatchMode,
        ) {
            dao.insert(WatchedAppEntity(packageName = app.packageName, label = app.label, mode = mode.key))
            Log.i(Log.Area.DB) { "watch pkg=${app.packageName} label=${app.label} mode=${mode.key}" }
        }

        override suspend fun setMode(
            packageName: String,
            mode: WatchMode,
        ) {
            val changed = dao.setMode(packageName, mode.key)
            Log.i(Log.Area.DB) { "set mode pkg=$packageName mode=${mode.key} rows=$changed" }
        }

        override suspend fun unwatch(packageName: String) {
            dao.delete(packageName)
            Log.i(Log.Area.DB) { "unwatch pkg=$packageName" }
        }
    }
