package com.aladin.foreground2.core.config

import com.aladin.foreground2.BuildConfig

/**
 * Gate timings. Debug builds use short values (breath 10 s, window 1 min) so the whole cycle can be tested by
 * hand; release uses the real ones (breath 60 s, window 10 min). Values come from BuildConfig fields set in
 * app/build.gradle.kts. Inject this (provided in di/) rather than reading BuildConfig directly.
 */
data class Timing(
    val breathDurationMillis: Long,
    val allowedWindowMillis: Long,
    val pollIntervalMillis: Long,
    /** How often the running service rechecks that Usage access and the overlay permission are still granted. */
    val permissionCheckIntervalMillis: Long,
    /** How often the service refreshes the home-screen widgets while the screen is on and unlocked. */
    val widgetRefreshIntervalMillis: Long,
    /** A widget never refreshes more often than this. */
    val widgetMinRefreshIntervalMillis: Long,
) {
    companion object {
        fun fromBuildConfig(): Timing =
            Timing(
                breathDurationMillis = BuildConfig.BREATH_DURATION_MS,
                allowedWindowMillis = BuildConfig.ALLOWED_WINDOW_MS,
                pollIntervalMillis = BuildConfig.POLL_INTERVAL_MS,
                permissionCheckIntervalMillis = BuildConfig.PERMISSION_CHECK_INTERVAL_MS,
                widgetRefreshIntervalMillis = BuildConfig.WIDGET_REFRESH_INTERVAL_MS,
                widgetMinRefreshIntervalMillis = BuildConfig.WIDGET_MIN_REFRESH_INTERVAL_MS,
            )
    }
}
