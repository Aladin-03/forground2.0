package com.aladin.foreground2.feature.stats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.core.domain.usage.DailyUsage
import com.aladin.foreground2.core.domain.usage.Week
import java.time.LocalDate

private object HourChart {
    /** Share of an hour slot's width taken by its bar. */
    const val BAR_FRACTION = 0.6f
}

/** Hours labelled under the hourly chart, one every quarter of the day. */
private val HourTicks = listOf(0, 6, 12, 18)

/** One stacked column per day, segmented by the week's top apps (stable colours) and Other. */
@Composable
fun ScreenTimeWeekChart(
    week: Week,
    today: LocalDate,
    selected: LocalDate,
    colourSlots: Map<String, Int>,
    onSelect: (LocalDate) -> Unit,
) {
    val sizing = ForegroundTheme.sizing
    val chartColors = ForegroundTheme.chartColors
    val max =
        week.days
            .maxOf { it?.totalMillis ?: 0L }
            .coerceAtLeast(1L)
            .toFloat()
    WeekColumns(week, today, selected, onSelect) { _, day ->
        val segments =
            day
                ?.let { usage ->
                    week.segments(usage).map { chartColors.forSlot(it.packageName?.let(colourSlots::get)) to it.millis }
                }.orEmpty()
        Canvas(Modifier.width(sizing.weekBarWidth).height(sizing.weekChartHeight)) {
            var top = size.height
            segments.forEach { (colour, millis) ->
                val height = size.height * millis / max
                drawRect(colour, topLeft = Offset(0f, top - height), size = Size(size.width, height))
                top -= height
            }
        }
    }
}

/** One plain column per day with its unlock count above it. */
@Composable
fun UnlocksWeekChart(
    week: Week,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val sizing = ForegroundTheme.sizing
    val colour = MaterialTheme.colorScheme.primary
    val max =
        week.days
            .maxOf { it?.unlocks ?: 0 }
            .coerceAtLeast(1)
            .toFloat()
    WeekColumns(week, today, selected, onSelect) { _, day ->
        Column(
            modifier = Modifier.height(sizing.weekChartHeight + sizing.barLabelHeight),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (day != null) {
                Text(
                    text = day.unlocks.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            val fraction = (day?.unlocks ?: 0) / max
            Canvas(Modifier.width(sizing.weekBarWidth).height(sizing.weekChartHeight * fraction)) {
                drawRect(colour)
            }
        }
    }
}

/**
 * Seven tappable day columns, Monday first, each with its weekday letter below. Today's column has a tinted
 * background, the selected one an outline. Days after today can't be selected.
 */
@Composable
private fun WeekColumns(
    week: Week,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    column: @Composable ColumnScope.(LocalDate, DailyUsage?) -> Unit,
) {
    val context = LocalContext.current
    val sizing = ForegroundTheme.sizing
    val shape = MaterialTheme.shapes.small
    val colours = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth()) {
        week.dates.forEach { date ->
            val isToday = date == today
            var modifier = Modifier.weight(1f).clip(shape)
            if (isToday) modifier = modifier.background(colours.primaryContainer)
            if (date == selected) modifier = modifier.border(sizing.selectionBorder, colours.primary, shape)
            Column(
                modifier =
                    modifier
                        .clickable(enabled = date <= today) { onSelect(date) }
                        .padding(vertical = ForegroundTheme.spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                column(date, week.day(date))
                Text(
                    text = formatWeekdayLetter(context, date),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isToday) FontWeight.Bold else null,
                    color = if (isToday) colours.primary else colours.onSurfaceVariant,
                    modifier = Modifier.padding(top = ForegroundTheme.spacing.xs),
                )
            }
        }
    }
}

/** 24 bars, one per local hour, for the selected day's unlocks. */
@Composable
fun HourlyUnlocksChart(unlocksByHour: List<Int>) {
    val colour = MaterialTheme.colorScheme.primary
    val max = (unlocksByHour.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
    Column {
        Text(
            text = stringResource(R.string.stats_by_hour),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = ForegroundTheme.spacing.sm),
        )
        Canvas(Modifier.fillMaxWidth().height(ForegroundTheme.sizing.hourChartHeight)) {
            val slot = size.width / unlocksByHour.size
            val barWidth = slot * HourChart.BAR_FRACTION
            unlocksByHour.forEachIndexed { hour, count ->
                val height = size.height * count / max
                drawRect(
                    colour,
                    topLeft = Offset(hour * slot + (slot - barWidth) / 2, size.height - height),
                    size = Size(barWidth, height),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            HourTicks.forEach { hour ->
                Text(
                    text = stringResource(R.string.stats_hour_label, hour),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A legend colour square. */
@Composable
fun Swatch(colour: Color) {
    Box(
        Modifier
            .size(ForegroundTheme.sizing.legendSwatch)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(colour),
    )
}
