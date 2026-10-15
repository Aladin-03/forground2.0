package com.aladin.foreground2.core.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.logging.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Apps the user can launch from the home screen (spec rule 1), excluding this app. */
interface LauncherAppSource {
    suspend fun launcherApps(): List<AppInfo>

    /** The app's launcher icon, or null if it is no longer installed. */
    suspend fun icon(packageName: String): ImageBitmap?
}

/**
 * Uses `queryIntentActivities(MAIN + LAUNCHER)`, visible through the matching `<queries>` entry in the
 * manifest. No QUERY_ALL_PACKAGES.
 */
@Singleton
class PackageManagerLauncherAppSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : LauncherAppSource {
        private val packageManager: PackageManager = context.packageManager
        private val icons = LruCache<String, ImageBitmap>(ICON_CACHE_SIZE)

        override suspend fun launcherApps(): List<AppInfo> =
            withContext(Dispatchers.IO) {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                queryLauncherActivities(intent)
                    .asSequence()
                    .map { it.activityInfo }
                    .filter { it.packageName != context.packageName }
                    // An app can have several launcher activities; keep one row per package.
                    .distinctBy { it.packageName }
                    .map { AppInfo(it.packageName, it.applicationInfo.loadLabel(packageManager).toString()) }
                    .toList()
                    .also { apps -> Log.d(Log.Area.DB) { "launcher apps loaded count=${apps.size}" } }
            }

        override suspend fun icon(packageName: String): ImageBitmap? {
            icons.get(packageName)?.let { return it }
            return withContext(Dispatchers.IO) {
                try {
                    packageManager
                        .getApplicationIcon(packageName)
                        .toBitmap()
                        .asImageBitmap()
                        .also { icons.put(packageName, it) }
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.w(Log.Area.DB, e) { "icon not found pkg=$packageName" }
                    null
                }
            }
        }

        private fun queryLauncherActivities(intent: Intent): List<ResolveInfo> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }

        private companion object {
            const val ICON_CACHE_SIZE = 200
        }
    }
