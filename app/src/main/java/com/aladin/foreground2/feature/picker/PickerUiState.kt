package com.aladin.foreground2.feature.picker

import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.watch.WatchMode
import com.aladin.foreground2.core.domain.watch.WatchedApp

data class PickerUiState(
    val loading: Boolean = true,
    val items: List<PickerItem> = emptyList(),
)

/**
 * One picker row. [mode] is the app's watch mode, or null if it isn't watched. [lockedUntil] is the end of its
 * running lock (epoch ms): a locked watched app can't be unwatched until then.
 */
data class PickerItem(
    val app: AppInfo,
    val mode: WatchMode? = null,
    val lockedUntil: Long? = null,
) {
    val watched: Boolean get() = mode != null
}

/**
 * Rows for the picker: every launcher app plus any watched app no longer on the launcher (so it can still be
 * unwatched), filtered by [query] on label or package, watched first, then alphabetical by label. [locks] maps
 * package name -> unlock time for running locks.
 */
internal fun pickerItems(
    launcherApps: List<AppInfo>,
    watched: List<WatchedApp>,
    locks: Map<String, Long>,
    query: String,
): List<PickerItem> {
    val modes = watched.associate { it.packageName to it.mode }
    val launcherPackages = launcherApps.mapTo(HashSet()) { it.packageName }
    val needle = query.trim()
    return (
        launcherApps +
            watched.filter { it.packageName !in launcherPackages }.map { AppInfo(it.packageName, it.label) }
    ).asSequence()
        .filter { needle.isEmpty() || it.label.contains(needle, true) || it.packageName.contains(needle, true) }
        .map { PickerItem(it, mode = modes[it.packageName], lockedUntil = locks[it.packageName]) }
        .sortedWith(
            compareByDescending<PickerItem> { it.watched }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.app.label }
                .thenBy { it.app.packageName },
        ).toList()
}
