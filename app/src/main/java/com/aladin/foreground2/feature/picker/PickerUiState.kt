package com.aladin.foreground2.feature.picker

import com.aladin.foreground2.core.domain.AppInfo

data class PickerUiState(
    val loading: Boolean = true,
    val items: List<PickerItem> = emptyList(),
)

data class PickerItem(
    val app: AppInfo,
    val watched: Boolean,
)

/**
 * Rows for the picker: every launcher app plus any watched app no longer on the launcher (so it can still be
 * unwatched), filtered by [query] on label or package, watched first, then alphabetical by label.
 */
internal fun pickerItems(
    launcherApps: List<AppInfo>,
    watched: List<AppInfo>,
    query: String,
): List<PickerItem> {
    val watchedPackages = watched.mapTo(HashSet()) { it.packageName }
    val launcherPackages = launcherApps.mapTo(HashSet()) { it.packageName }
    val needle = query.trim()
    return (launcherApps + watched.filter { it.packageName !in launcherPackages })
        .asSequence()
        .filter { needle.isEmpty() || it.label.contains(needle, true) || it.packageName.contains(needle, true) }
        .map { PickerItem(it, watched = it.packageName in watchedPackages) }
        .sortedWith(
            compareByDescending<PickerItem> { it.watched }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.app.label }
                .thenBy { it.app.packageName },
        ).toList()
}
