package com.aladin.foreground2.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Routes the system's update requests (widget placed, `updatePeriodMillis` fallback) through [WidgetRefresher], so
 * they obey the same throttle and screen-off rule as the service's refreshes.
 */
abstract class ThrottledWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        context.widgetRefresher().refreshInBackground(RefreshReason.System, appWidgetIds.toSet()) { pending.finish() }
    }
}

class UnlocksWidgetReceiver : ThrottledWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UnlocksWidget()
}

class ScreenTimeWidgetReceiver : ThrottledWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScreenTimeWidget()
}
