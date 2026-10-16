package com.aladin.foreground2.core.domain.usage

/** The usage-log event kinds the daily stats need. core/data maps the system's events onto these. */
enum class UsageEventType {
    ActivityResumed,
    ActivityPaused,
    KeyguardShown,
    KeyguardHidden,
    ScreenNonInteractive,
}

/** One usage-log event at [timestamp] (epoch ms). [className] is the activity, null for device-level events. */
data class UsageEvent(
    val type: UsageEventType,
    val packageName: String,
    val className: String?,
    val timestamp: Long,
)
