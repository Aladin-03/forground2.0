package com.aladin.foreground2.service.overlay

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.aladin.foreground2.core.logging.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Sends the user to the home screen (the lock screen's Go home). */
fun interface HomeLauncher {
    fun goHome()
}

/**
 * Fires the HOME intent. Allowed from the background because the app holds Display over other apps
 * (SYSTEM_ALERT_WINDOW is a background-activity-start exemption).
 */
@Singleton
class IntentHomeLauncher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : HomeLauncher {
        override fun goHome() {
            val home =
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(home)
            } catch (e: ActivityNotFoundException) {
                Log.e(Log.Area.Overlay, e) { "no home activity" }
            }
        }
    }
