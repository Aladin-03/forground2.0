package com.aladin.foreground2.widget

import android.content.Context
import android.os.PowerManager
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.data.repo.UsageStatsRepository
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.usage.startOfDayMillis
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** One app in a widget's top-apps list. */
data class TopApp(
    val label: String,
    val millis: Long,
)

/** What the widgets show: today's numbers as of one refresh. */
data class WidgetSnapshot(
    val startOfDay: Long,
    val unlocks: Int,
    val totalMillis: Long,
    val topApps: List<TopApp>,
)

/**
 * The only way the widgets are refreshed. Reads today's stats once per refresh into [snapshot], which both widgets
 * render, then updates each placed widget that [RefreshThrottle] allows (at most once a minute, but always on a new
 * day). A throttled widget gets one deferred refresh once it is allowed again. Nothing happens while the screen is off.
 */
@Singleton
class WidgetRefresher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val usageStatsRepository: UsageStatsRepository,
        private val launcherAppSource: LauncherAppSource,
        private val clock: Clock,
        timing: Timing,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        private val power = context.getSystemService(PowerManager::class.java)
        private val throttle = RefreshThrottle(timing.widgetMinRefreshIntervalMillis)
        private val mutex = Mutex()
        private var deferred: Job? = null

        private val state = MutableStateFlow<WidgetSnapshot?>(null)
        val snapshot: StateFlow<WidgetSnapshot?> = state.asStateFlow()

        /** The latest snapshot if it is from today, else a fresh one. For a widget rendering outside a refresh. */
        suspend fun current(): WidgetSnapshot {
            val latest = state.value
            val today = startOfDayMillis(clock.nowMillis(), ZoneId.systemDefault())
            return if (latest != null && latest.startOfDay == today) latest else load()
        }

        /** Refreshes every placed widget, or only [appWidgetIds] (a system update request). */
        suspend fun refresh(
            reason: RefreshReason,
            appWidgetIds: Set<Int>? = null,
        ) = mutex.withLock {
            if (!power.isInteractive) {
                Log.d(Log.Area.Widget) { "skip reason=$reason: screen off" }
                return@withLock
            }
            val manager = GlanceAppWidgetManager(context)
            val placed =
                widgets().flatMap { widget ->
                    manager.getGlanceIds(widget.javaClass).map { Placed(widget, it, manager.getAppWidgetId(it)) }
                }
            throttle.retain(placed.map { it.appWidgetId }.toSet())
            val now = clock.nowMillis()
            val startOfDay = startOfDayMillis(now, ZoneId.systemDefault())
            val (due, throttled) =
                placed
                    .filter { appWidgetIds == null || it.appWidgetId in appWidgetIds }
                    .partition { throttle.tryAcquire(it.appWidgetId, now, startOfDay) }
            if (throttled.isNotEmpty()) {
                Log.d(Log.Area.Widget) { "throttled reason=$reason ids=${throttled.map { it.appWidgetId }}" }
                scheduleDeferred(throttled.minOf { throttle.nextAllowedAt(it.appWidgetId) } - now)
            }
            if (due.isEmpty()) return@withLock
            val data = load()
            due.forEach {
                Log.i(Log.Area.Widget) {
                    "refresh ${it.widget.javaClass.simpleName} id=${it.appWidgetId} reason=$reason " +
                        "unlocks=${data.unlocks} totalMin=${data.totalMillis / MINUTE_MILLIS}"
                }
                it.widget.update(context, it.glanceId)
            }
        }

        /** Same as [refresh], from a broadcast receiver: runs in the app scope and calls [done] when finished. */
        fun refreshInBackground(
            reason: RefreshReason,
            appWidgetIds: Set<Int>,
            done: () -> Unit,
        ) {
            scope.launch {
                try {
                    refresh(reason, appWidgetIds)
                } finally {
                    done()
                }
            }
        }

        private fun scheduleDeferred(delayMillis: Long) {
            if (deferred?.isActive == true) return
            deferred =
                scope.launch {
                    delay(delayMillis)
                    deferred = null
                    refresh(RefreshReason.Deferred)
                }
        }

        private suspend fun load(): WidgetSnapshot {
            val now = clock.nowMillis()
            val usage = usageStatsRepository.today()
            val top =
                usage.apps.take(TOP_APPS).map {
                    TopApp(launcherAppSource.label(it.packageName) ?: it.packageName, it.foregroundMillis)
                }
            return WidgetSnapshot(startOfDayMillis(now, ZoneId.systemDefault()), usage.unlocks, usage.totalMillis, top)
                .also { state.value = it }
        }

        private class Placed(
            val widget: GlanceAppWidget,
            val glanceId: GlanceId,
            val appWidgetId: Int,
        )

        private companion object {
            const val TOP_APPS = 3
            const val MINUTE_MILLIS = 60_000L

            fun widgets(): List<GlanceAppWidget> = listOf(UnlocksWidget(), ScreenTimeWidget())
        }
    }
