package com.aladin.foreground2.core.data.repo

import com.aladin.foreground2.core.data.db.WatchedAppDao
import com.aladin.foreground2.core.data.db.WatchedAppEntity
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.logging.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The set of apps the user chose to watch (spec rule 1). */
interface WatchedAppsRepository {
    fun observeWatched(): Flow<List<AppInfo>>

    suspend fun watch(app: AppInfo)

    suspend fun unwatch(packageName: String)
}

@Singleton
class RoomWatchedAppsRepository
    @Inject
    constructor(
        private val dao: WatchedAppDao,
    ) : WatchedAppsRepository {
        override fun observeWatched(): Flow<List<AppInfo>> =
            dao.observeAll().map { rows -> rows.map { AppInfo(it.packageName, it.label) } }

        override suspend fun watch(app: AppInfo) {
            dao.insert(WatchedAppEntity(packageName = app.packageName, label = app.label))
            Log.i(Log.Area.DB) { "watch pkg=${app.packageName} label=${app.label}" }
        }

        override suspend fun unwatch(packageName: String) {
            dao.delete(packageName)
            Log.i(Log.Area.DB) { "unwatch pkg=$packageName" }
        }
    }
