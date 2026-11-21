package com.aladin.foreground2.core.config

import com.aladin.foreground2.BuildConfig
import com.aladin.foreground2.core.domain.watch.WatchMode

/**
 * Gate timings. Debug builds use short values (breath 10 s; windows 1 min / 30 s / 10 s; sit-through 20 s) so the
 * whole cycle can be tested by hand; release uses the real ones (breath 60 s; windows 10 / 5 / 1 min; sit-through
 * 5 min). Values come from BuildConfig fields set in
 * app/build.gradle.kts. Inject this (provided in di/) rather than reading BuildConfig directly.
 */
data class Timing(
    val breathDurationMillis: Long,
    /** Allowed window after a No, per [WatchMode]: Mindful, Focused, Strict. */
    val mindfulWindowMillis: Long,
    val focusedWindowMillis: Long,
    val strictWindowMillis: Long,
    val pollIntervalMillis: Long,
    /** How often the running service rechecks that Usage access and the overlay permission are still granted. */
    val permissionCheckIntervalMillis: Long,
    /** How often the service refreshes the home-screen widgets while the screen is on and unlocked. */
    val widgetRefreshIntervalMillis: Long,
    /** A widget never refreshes more often than this. */
    val widgetMinRefreshIntervalMillis: Long,
    /** The countdown the user sits through before loosening an app's mode or unwatching it. */
    val sitThroughMillis: Long,
) {
    /** How long [mode]'s allowed window lasts. */
    fun windowMillis(mode: WatchMode): Long =
        when (mode) {
            WatchMode.MINDFUL -> mindfulWindowMillis
            WatchMode.FOCUSED -> focusedWindowMillis
            WatchMode.STRICT -> strictWindowMillis
        }

    companion object {
        fun fromBuildConfig(): Timing =
            Timing(
                breathDurationMillis = BuildConfig.BREATH_DURATION_MS,
                mindfulWindowMillis = BuildConfig.MINDFUL_WINDOW_MS,
                focusedWindowMillis = BuildConfig.FOCUSED_WINDOW_MS,
                strictWindowMillis = BuildConfig.STRICT_WINDOW_MS,
                pollIntervalMillis = BuildConfig.POLL_INTERVAL_MS,
                permissionCheckIntervalMillis = BuildConfig.PERMISSION_CHECK_INTERVAL_MS,
                widgetRefreshIntervalMillis = BuildConfig.WIDGET_REFRESH_INTERVAL_MS,
                widgetMinRefreshIntervalMillis = BuildConfig.WIDGET_MIN_REFRESH_INTERVAL_MS,
                sitThroughMillis = BuildConfig.SIT_THROUGH_MS,
            )
    }
}
