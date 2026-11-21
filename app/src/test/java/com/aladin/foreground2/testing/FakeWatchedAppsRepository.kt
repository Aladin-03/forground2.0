package com.aladin.foreground2.testing

import com.aladin.foreground2.core.data.repo.WatchedAppsRepository
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.watch.WatchMode
import com.aladin.foreground2.core.domain.watch.WatchedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [WatchedAppsRepository] with the Room one's rules: watching a watched app changes nothing. */
class FakeWatchedAppsRepository(
    apps: List<WatchedApp> = emptyList(),
) : WatchedAppsRepository {
    val watched = MutableStateFlow(apps)

    override fun observeWatched(): Flow<List<WatchedApp>> = watched

    override suspend fun watch(
        app: AppInfo,
        mode: WatchMode,
    ) {
        if (watched.value.none { it.packageName == app.packageName }) {
            watched.value += WatchedApp(app.packageName, app.label, mode)
        }
    }

    override suspend fun setMode(
        packageName: String,
        mode: WatchMode,
    ) {
        watched.value = watched.value.map { if (it.packageName == packageName) it.copy(mode = mode) else it }
    }

    override suspend fun unwatch(packageName: String) {
        watched.value = watched.value.filterNot { it.packageName == packageName }
    }

    fun modeOf(packageName: String): WatchMode? = watched.value.find { it.packageName == packageName }?.mode
}

fun AppInfo.watchedAs(mode: WatchMode = WatchMode.MINDFUL) = WatchedApp(packageName, label, mode)
