package com.aladin.foreground2.core.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.aladin.foreground2.core.logging.Log

/**
 * Starts the first of [intents] the system accepts and returns true, or false if none did. OEM builds often
 * drop or un-export Settings screens, so a missing or protected target just moves on to the next one.
 * Call with an Activity context.
 */
fun Context.startFirstAvailable(
    intents: List<Intent>,
    label: String,
): Boolean {
    val started =
        intents.firstOrNull { intent ->
            try {
                startActivity(intent)
                true
            } catch (e: ActivityNotFoundException) {
                Log.w(Log.Area.Perm, e) { "open $label: not found ${intent.describe()}" }
                false
            } catch (e: SecurityException) {
                Log.w(Log.Area.Perm, e) { "open $label: not allowed ${intent.describe()}" }
                false
            }
        }
    if (started != null) {
        Log.i(Log.Area.Perm) { "open $label -> ${started.describe()}" }
    } else {
        Log.e(Log.Area.Perm) { "open $label: no settings screen could be opened" }
    }
    return started != null
}

private fun Intent.describe(): String =
    component?.flattenToShortString() ?: listOfNotNull(action, data).joinToString(" ")

/** Opens the Settings screen that grants [permission], falling back to more general screens. */
fun Context.openSettingsFor(permission: Permission): Boolean =
    startFirstAvailable(SettingsIntents.forPermission(permission, packageName), label = permission.name)

/** Opens an OEM autostart screen if one exists, otherwise this app's App info page. */
fun Context.openAutostartSettings(): Boolean =
    startFirstAvailable(SettingsIntents.autostart(packageName), label = "Autostart")
