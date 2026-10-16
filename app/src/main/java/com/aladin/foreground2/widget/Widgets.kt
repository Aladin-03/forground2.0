package com.aladin.foreground2.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.aladin.foreground2.MainActivity
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.ForegroundWidgetColors
import com.aladin.foreground2.core.designsystem.WidgetTokens
import com.aladin.foreground2.core.ui.formatUsageDuration

/** Small widget: "Unlocks today" and the count. */
class UnlocksWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val refresher = context.widgetRefresher()
        val initial = refresher.current()
        provideContent {
            val snapshot by refresher.snapshot.collectAsState()
            WidgetFrame {
                Label(LocalContext.current.getString(R.string.widget_unlocks_label))
                Value((snapshot ?: initial).unlocks.toString())
            }
        }
    }
}

/** Small: "Screen time today" and the total. Medium adds the top three apps. */
class ScreenTimeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(WidgetTokens.small, WidgetTokens.medium))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val refresher = context.widgetRefresher()
        val initial = refresher.current()
        provideContent {
            val snapshot by refresher.snapshot.collectAsState()
            ScreenTimeContent(snapshot ?: initial)
        }
    }
}

@Composable
private fun ScreenTimeContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val size = LocalSize.current
    val medium = size.width >= WidgetTokens.medium.width && size.height >= WidgetTokens.medium.height
    WidgetFrame {
        Label(context.getString(R.string.widget_screen_time_label))
        Value(formatUsageDuration(context, snapshot.totalMillis))
        if (medium) {
            Spacer(GlanceModifier.height(WidgetTokens.gap))
            if (snapshot.topApps.isEmpty()) {
                Label(context.getString(R.string.stats_apps_empty))
            }
            snapshot.topApps.forEach { app ->
                Row(GlanceModifier.fillMaxWidth().padding(top = WidgetTokens.gap)) {
                    Text(
                        text = app.label,
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = WidgetTokens.rowText),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Text(
                        text = formatUsageDuration(context, app.millis),
                        style =
                            TextStyle(
                                color = GlanceTheme.colors.onBackground,
                                fontSize = WidgetTokens.rowText,
                                fontWeight = FontWeight.Medium,
                            ),
                    )
                }
            }
        }
    }
}

/** Themed rounded background; a tap opens the Stats screen. */
@Composable
private fun WidgetFrame(content: @Composable () -> Unit) {
    val context = LocalContext.current
    GlanceTheme(colors = ForegroundWidgetColors) {
        Column(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.background)
                    .cornerRadius(WidgetTokens.cornerRadius)
                    .padding(WidgetTokens.padding)
                    .clickable(actionStartActivity(MainActivity.openStatsIntent(context))),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = WidgetTokens.labelText),
    )
}

@Composable
private fun Value(text: String) {
    Text(
        text = text,
        maxLines = 1,
        style =
            TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = WidgetTokens.valueText,
                fontWeight = FontWeight.Bold,
            ),
    )
}
