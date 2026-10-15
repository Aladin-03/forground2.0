package com.aladin.foreground2.core.permissions

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Settings screens to grant each permission, most specific first. Every list ends in a generic screen, so
 * [startFirstAvailable] always has something to fall back to.
 */
object SettingsIntents {
    fun forPermission(
        permission: Permission,
        packageName: String,
    ): List<Intent> =
        when (permission) {
            Permission.UsageAccess ->
                listOf(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri(packageName)),
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                )
            Permission.Overlay ->
                listOf(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(packageName)),
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
                )
            Permission.Notifications ->
                listOf(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                    appDetails(packageName),
                )
            Permission.BatteryOptimization -> batteryOptimization(packageName)
        } + generalSettings()

    /** Known Transsion (Infinix/Tecno/itel) autostart screens, then this app's App info page. */
    fun autostart(packageName: String): List<Intent> =
        TranssionAutostart.map { Intent().setComponent(it) } + appDetails(packageName) + generalSettings()

    // Direct exemption prompt; the app is sideloaded, so the Play policy on this permission doesn't apply.
    @SuppressLint("BatteryLife")
    private fun batteryOptimization(packageName: String): List<Intent> =
        listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(packageName)),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )

    private fun appDetails(packageName: String) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(packageName))

    private fun generalSettings() = Intent(Settings.ACTION_SETTINGS)

    private fun packageUri(packageName: String): Uri = Uri.fromParts("package", packageName, null)

    private val TranssionAutostart =
        listOf(
            ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"),
            ComponentName("com.transsion.phonemanager", "com.itel.autobootmanager.activity.AutoBootMgrActivity"),
        )
}
