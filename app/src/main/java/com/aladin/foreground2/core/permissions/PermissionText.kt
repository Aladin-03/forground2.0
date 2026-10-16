package com.aladin.foreground2.core.permissions

import android.content.Context
import androidx.annotation.StringRes
import com.aladin.foreground2.R

/** User-facing name of the permission, e.g. "Usage access". */
@get:StringRes
val Permission.titleRes: Int
    get() =
        when (this) {
            Permission.UsageAccess -> R.string.permission_usage_title
            Permission.Overlay -> R.string.permission_overlay_title
            Permission.Notifications -> R.string.permission_notifications_title
            Permission.BatteryOptimization -> R.string.permission_battery_title
        }

/** One sentence on why Foreground needs the permission. */
@get:StringRes
val Permission.bodyRes: Int
    get() =
        when (this) {
            Permission.UsageAccess -> R.string.permission_usage_body
            Permission.Overlay -> R.string.permission_overlay_body
            Permission.Notifications -> R.string.permission_notifications_body
            Permission.BatteryOptimization -> R.string.permission_battery_body
        }

/** The permissions' names as one readable list, e.g. "Usage access, Display over other apps". */
fun List<Permission>.names(context: Context): String =
    joinToString(context.getString(R.string.permission_list_separator)) { context.getString(it.titleRes) }
