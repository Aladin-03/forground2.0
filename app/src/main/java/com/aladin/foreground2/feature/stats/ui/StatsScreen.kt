package com.aladin.foreground2.feature.stats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.core.domain.usage.AppUsage
import com.aladin.foreground2.core.domain.usage.DailyUsage
import com.aladin.foreground2.core.domain.usage.Week
import com.aladin.foreground2.core.domain.usage.buildWeeks
import com.aladin.foreground2.core.domain.usage.colourSlots
import com.aladin.foreground2.core.ui.AppIcon
import com.aladin.foreground2.core.ui.formatUsageDuration
import com.aladin.foreground2.feature.stats.StatsTab
import com.aladin.foreground2.feature.stats.StatsUiState
import com.aladin.foreground2.feature.stats.StatsViewModel
import java.time.LocalDate

@Composable
fun StatsScreen(
    initialTab: StatsTab,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    StatsContent(
        state = state,
        initialTab = initialTab,
        onSelectDay = viewModel::onSelectDay,
        loadIcon = viewModel::loadIcon,
        modifier = modifier,
    )
}

@Composable
private fun StatsContent(
    state: StatsUiState,
    initialTab: StatsTab,
    onSelectDay: (LocalDate, LocalDate) -> Unit,
    loadIcon: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.stats_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(ForegroundTheme.spacing.md),
        )
        TabRow(selectedTabIndex = tab.ordinal) {
            StatsTab.entries.forEach {
                Tab(selected = it == tab, onClick = { tab = it }, text = { Text(tabTitle(it)) })
            }
        }
        val today = state.today
        if (state.loading || today == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        // Oldest week first, so the pager opens on the last page and swiping right goes back in time.
        val pager = rememberPagerState(initialPage = state.weeks.lastIndex) { state.weeks.size }
        HorizontalPager(
            state = pager,
            key = { state.weeks[it].start.toEpochDay() },
            modifier = Modifier.weight(1f),
        ) { page ->
            val week = state.weeks[page]
            WeekPage(
                week = week,
                tab = tab,
                state = state,
                onSelectDay = { onSelectDay(week.start, it) },
                loadIcon = loadIcon,
            )
        }
    }
}

@Composable
private fun tabTitle(tab: StatsTab): String =
    stringResource(
        when (tab) {
            StatsTab.Unlocks -> R.string.stats_tab_unlocks
            StatsTab.ScreenTime -> R.string.stats_tab_screen_time
        },
    )

@Composable
private fun WeekPage(
    week: Week,
    tab: StatsTab,
    state: StatsUiState,
    onSelectDay: (LocalDate) -> Unit,
    loadIcon: suspend (String) -> ImageBitmap?,
) {
    val spacing = ForegroundTheme.spacing
    val today = checkNotNull(state.today)
    val selected = state.selectedDay(week)
    val day = week.day(selected)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item { WeekHeader(week, tab) }
        if (!week.hasData) {
            item { EmptyText(stringResource(R.string.stats_week_empty)) }
            return@LazyColumn
        }
        item {
            when (tab) {
                StatsTab.Unlocks -> UnlocksWeekChart(week, today, selected, onSelectDay)
                StatsTab.ScreenTime ->
                    ScreenTimeWeekChart(week, today, selected, state.colourSlots, onSelectDay)
            }
        }
        if (tab == StatsTab.ScreenTime) {
            item { Legend(week = week, state = state, loadIcon = loadIcon) }
        }
        item { DayHeader(selected, day, tab) }
        when {
            day == null -> item { EmptyText(stringResource(R.string.stats_day_empty)) }
            tab == StatsTab.Unlocks -> item { HourlyUnlocksChart(day.unlocksByHour) }
            day.apps.isEmpty() -> item { EmptyText(stringResource(R.string.stats_apps_empty)) }
            else ->
                items(day.apps, key = { it.packageName }) { app ->
                    AppTimeRow(app = app, week = week, state = state, loadIcon = loadIcon)
                }
        }
    }
}

@Composable
private fun WeekHeader(
    week: Week,
    tab: StatsTab,
) {
    val context = LocalContext.current
    Column {
        Text(
            text =
                stringResource(
                    R.string.stats_week_range,
                    formatDayMonth(context, week.dates.first()),
                    formatDayMonth(context, week.dates.last()),
                ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        val average =
            when (tab) {
                StatsTab.Unlocks ->
                    week.averageUnlocks?.let { stringResource(R.string.stats_average_unlocks, it) }
                StatsTab.ScreenTime ->
                    week.averageMillis?.let {
                        stringResource(R.string.stats_average_screen_time, formatUsageDuration(context, it))
                    }
            }
        if (average != null) {
            Text(
                text = average,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayHeader(
    date: LocalDate,
    day: DailyUsage?,
    tab: StatsTab,
) {
    val context = LocalContext.current
    Column {
        Text(
            text = formatFullDay(context, date),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (day != null) {
            Text(
                text =
                    when (tab) {
                        StatsTab.Unlocks ->
                            pluralStringResource(R.plurals.stats_unlocks_count, day.unlocks, day.unlocks)
                        StatsTab.ScreenTime -> formatUsageDuration(context, day.totalMillis)
                    },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** The week's top apps and Other, with their colours and week totals. */
@Composable
private fun Legend(
    week: Week,
    state: StatsUiState,
    loadIcon: suspend (String) -> ImageBitmap?,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(ForegroundTheme.spacing.xs)) {
        week.totals().forEach { segment ->
            val pkg = segment.packageName
            ColouredRow(
                colour = ForegroundTheme.chartColors.forSlot(pkg?.let(state.colourSlots::get)),
                packageName = pkg,
                label = pkg?.let(state::label) ?: stringResource(R.string.stats_other),
                value = formatUsageDuration(context, segment.millis),
                loadIcon = loadIcon,
            )
        }
    }
}

/** One app's time on the selected day, in its chart colour (grey when it is part of Other this week). */
@Composable
private fun AppTimeRow(
    app: AppUsage,
    week: Week,
    state: StatsUiState,
    loadIcon: suspend (String) -> ImageBitmap?,
) {
    val slot = if (app.packageName in week.topApps) state.colourSlots[app.packageName] else null
    ColouredRow(
        colour = ForegroundTheme.chartColors.forSlot(slot),
        packageName = app.packageName,
        label = state.label(app.packageName),
        value = formatUsageDuration(LocalContext.current, app.foregroundMillis),
        loadIcon = loadIcon,
    )
}

@Composable
private fun ColouredRow(
    colour: Color,
    packageName: String?,
    label: String,
    value: String,
    loadIcon: suspend (String) -> ImageBitmap?,
) {
    val sizing = ForegroundTheme.sizing
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ForegroundTheme.spacing.sm),
    ) {
        Swatch(colour)
        if (packageName != null) {
            AppIcon(packageName = packageName, loadIcon = loadIcon, size = sizing.smallAppIcon)
        } else {
            Spacer(Modifier.size(sizing.smallAppIcon))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Suppress("MagicNumber")
@Preview(showBackground = true)
@Composable
private fun StatsContentPreview() {
    val today = LocalDate.of(2026, 10, 16)
    val days =
        (0..10).map { back ->
            DailyUsage(
                date = today.minusDays(back.toLong()),
                unlocksByHour = List(DailyUsage.HOURS) { hour -> (hour + back) % 5 },
                apps =
                    listOf(
                        AppUsage("com.google.android.youtube", foregroundMillis = 3_600_000L + back * 600_000L),
                        AppUsage("com.whatsapp", foregroundMillis = 1_800_000L),
                        AppUsage("com.android.chrome", foregroundMillis = 900_000L + back * 60_000L),
                    ),
            )
        }
    val weeks = buildWeeks(days, today)
    ForegroundTheme {
        StatsContent(
            state =
                StatsUiState(
                    loading = false,
                    today = today,
                    weeks = weeks,
                    colourSlots = colourSlots(weeks, slots = 10),
                    labels = mapOf("com.google.android.youtube" to "YouTube", "com.whatsapp" to "WhatsApp"),
                ),
            initialTab = StatsTab.ScreenTime,
            onSelectDay = { _, _ -> },
            loadIcon = { null },
        )
    }
}
