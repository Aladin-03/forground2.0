package com.aladin.foreground2.feature.home

import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.permissions.Permission

/** A running lock, as listed on the home screen. */
data class LockedApp(
    val app: AppInfo,
    val unlockAtMillis: Long,
)

/** A watched app, as listed on the home screen: locked, or gets a breath on its next entry. */
data class WatchedApp(
    val app: AppInfo,
    val locked: Boolean,
)

/** What the status row's button does. */
sealed interface HomeFix {
    /** Open the Settings screen that grants [permission]. */
    data class Grant(
        val permission: Permission,
    ) : HomeFix

    /** Start the detection service. */
    data object StartService : HomeFix
}

data class HomeUiState(
    /** Running locks, soonest to unlock first. */
    val locks: List<LockedApp> = emptyList(),
    /** Every watched app, by label. */
    val watched: List<WatchedApp> = emptyList(),
    /** Every permission not granted right now, required or recommended. */
    val missing: List<Permission> = emptyList(),
    val serviceRunning: Boolean = false,
) {
    val missingRequired: List<Permission> get() = missing.filter { it.required }

    /**
     * The most urgent problem, or null when all is well: a missing required permission (nothing works without it),
     * then a stopped service, then a missing recommended one (battery, which XOS uses to kill the service).
     */
    val fix: HomeFix?
        get() =
            when {
                missingRequired.isNotEmpty() -> HomeFix.Grant(missingRequired.first())
                !serviceRunning -> HomeFix.StartService
                missing.isNotEmpty() -> HomeFix.Grant(missing.first())
                else -> null
            }
}
