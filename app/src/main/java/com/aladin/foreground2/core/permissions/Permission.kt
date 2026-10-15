package com.aladin.foreground2.core.permissions

/** A permission or system setting onboarding asks for. [required] ones must be granted before home is reachable. */
enum class Permission(
    val required: Boolean,
) {
    UsageAccess(required = true),
    Overlay(required = true),
    Notifications(required = true),
    BatteryOptimization(required = false),
}
