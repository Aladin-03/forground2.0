package com.aladin.foreground2.core.logging

import com.aladin.foreground2.BuildConfig
import android.util.Log as AndroidLog

/**
 * The app's only logger. Every line uses tag "FG2" and starts with an area, e.g. `[Gate] Idle -> Breathing`.
 * Filter on device with `adb logcat -s FG2`. Disabled in release builds; messages are lambdas so they cost
 * nothing when off.
 *
 * Every gate state transition, overlay show/hide, service start/stop and permission change is logged.
 */
object Log {
    const val TAG = "FG2"

    enum class Area(
        val label: String,
    ) {
        App("App"),
        Detect("Detect"),
        Gate("Gate"),
        Overlay("Overlay"),
        DB("DB"),
        Perm("Perm"),
        UI("UI"),
    }

    @Volatile
    var enabled: Boolean = BuildConfig.LOGGING_ENABLED

    fun d(
        area: Area,
        message: () -> String,
    ) {
        if (enabled) AndroidLog.d(TAG, format(area, message()))
    }

    fun i(
        area: Area,
        message: () -> String,
    ) {
        if (enabled) AndroidLog.i(TAG, format(area, message()))
    }

    fun w(
        area: Area,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        if (enabled) AndroidLog.w(TAG, format(area, message()), throwable)
    }

    fun e(
        area: Area,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        if (enabled) AndroidLog.e(TAG, format(area, message()), throwable)
    }

    internal fun format(
        area: Area,
        message: String,
    ): String = "[${area.label}] $message"
}
