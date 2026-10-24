package com.aladin.foreground2.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.aladin.foreground2.core.config.PillPosition
import com.aladin.foreground2.core.data.repo.UsageStatsRepository
import com.aladin.foreground2.core.designsystem.PillTokens
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.di.ApplicationScope
import com.aladin.foreground2.service.overlay.ui.UsagePillContent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Rule 10: the small "time left · used today" pill shown during an allowed window. Main thread only. */
interface UsagePill {
    /** Shows the pill for [packageName], whose allowed window ends at [until] (epoch ms), replacing any other. */
    @MainThread
    fun show(
        packageName: String,
        until: Long,
    )

    /** Removes the pill if it is showing (or loading) for [packageName]. */
    @MainThread
    fun hide(packageName: String)

    /** Removes the pill, whichever app it is for (the service is stopping). */
    @MainThread
    fun hideAll()
}

/**
 * [UsagePill] in its own small TYPE_APPLICATION_OVERLAY window, separate from [OverlayHost]'s full-screen one: not
 * focusable and not touchable, at [PillTokens.WINDOW_ALPHA], so every tap and key goes to the app below. Today's use
 * of the app is read once from [UsageStatsRepository] when it is shown; the pill then adds the time since, so nothing
 * queries the usage log while it ticks. It doesn't pause media.
 */
@Singleton
class WindowUsagePill
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val clock: Clock,
        private val usageStats: UsageStatsRepository,
        @ApplicationScope private val scope: CoroutineScope,
    ) : UsagePill {
        private class Shown(
            val packageName: String,
            val root: View,
            val owner: OverlayOwner,
        )

        private val windowManager = context.getSystemService(WindowManager::class.java)

        /** The app the pill is for, from [show] (while its usage loads) until [hide]. */
        private var shownFor: String? = null
        private var loading: Job? = null
        private var current: Shown? = null

        override fun show(
            packageName: String,
            until: Long,
        ) {
            hideAll()
            shownFor = packageName
            loading =
                scope.launch(Dispatchers.Main.immediate) {
                    val readAt = clock.nowMillis()
                    val usedToday =
                        usageStats
                            .today()
                            .apps
                            .find { it.packageName == packageName }
                            ?.foregroundMillis ?: 0L
                    addWindow(packageName, until, usedToday, readAt)
                }
        }

        override fun hide(packageName: String) {
            if (shownFor == packageName) hideAll()
        }

        override fun hideAll() {
            val pkg = shownFor ?: return
            shownFor = null
            loading?.cancel()
            loading = null
            val shown = current
            if (shown == null) {
                Log.i(Log.Area.Pill) { "hide $pkg (before it showed)" }
                return
            }
            current = null
            shown.owner.destroy()
            try {
                windowManager.removeViewImmediate(shown.root)
            } catch (e: IllegalArgumentException) {
                Log.w(Log.Area.Pill, e) { "pill for ${shown.packageName} was not attached" }
            }
            Log.i(Log.Area.Pill) { "hide ${shown.packageName}" }
        }

        private fun addWindow(
            packageName: String,
            until: Long,
            usedToday: Long,
            readAt: Long,
        ) {
            val owner = OverlayOwner()
            val view =
                ComposeView(context).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner))
                    setContent {
                        UsagePillContent(until = until, usedToday = usedToday, usageReadAt = readAt, clock = clock)
                    }
                }
            owner.attachTo(view)
            owner.resume()
            try {
                windowManager.addView(view, layoutParams())
            } catch (e: WindowManager.BadTokenException) {
                // Overlay permission revoked; nothing to show.
                Log.e(Log.Area.Pill, e) { "show $packageName failed" }
                owner.destroy()
                return
            }
            current = Shown(packageName, view, owner)
            Log.i(Log.Area.Pill) { "show $packageName until=$until usedToday=${usedToday / SECOND_MILLIS}s" }
        }

        /**
         * Top-right, just below the status bar by [PillPosition]. LAYOUT_NO_LIMITS and the cutout mode keep it at
         * the same spot in portrait, landscape and fullscreen video, whatever the system bars do.
         */
        private fun layoutParams() =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    title = "FG2 usage pill"
                    gravity = Gravity.TOP or Gravity.END
                    x = dpToPx(PillPosition.END_OFFSET_DP)
                    y = dpToPx(PillPosition.TOP_OFFSET_DP)
                    alpha = PillTokens.WINDOW_ALPHA
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
                }

        private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).roundToInt()

        private companion object {
            const val SECOND_MILLIS = 1_000L
        }
    }
