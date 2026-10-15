package com.aladin.foreground2.core.permissions

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the current grant state. Cheap and synchronous, so callers can recheck on every resume. */
interface PermissionChecker {
    fun isGranted(permission: Permission): Boolean
}

fun PermissionChecker.missingRequired(): List<Permission> = Permission.entries.filter { it.required && !isGranted(it) }

@Singleton
class AndroidPermissionChecker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PermissionChecker {
        override fun isGranted(permission: Permission): Boolean =
            when (permission) {
                Permission.UsageAccess -> hasUsageAccess()
                Permission.Overlay -> Settings.canDrawOverlays(context)
                Permission.Notifications -> hasNotifications()
                Permission.BatteryOptimization ->
                    context
                        .getSystemService(
                            PowerManager::class.java,
                        ).isIgnoringBatteryOptimizations(context.packageName)
            }

        private fun hasUsageAccess(): Boolean {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            val mode =
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            // MODE_DEFAULT defers to the manifest permission grant (seen on some OEM builds).
            return if (mode == AppOpsManager.MODE_DEFAULT) {
                context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                mode == AppOpsManager.MODE_ALLOWED
            }
        }

        private fun hasNotifications(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
    }
