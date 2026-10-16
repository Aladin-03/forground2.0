package com.aladin.foreground2.feature.stats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.usage.UsageDuration
import com.aladin.foreground2.core.ui.AppIcon
import com.aladin.foreground2.feature.stats.AppTime
import com.aladin.foreground2.feature.stats.StatsUiState
import com.aladin.foreground2.feature.stats.StatsViewModel

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    StatsContent(state = state, loadIcon = viewModel::loadIcon, modifier = modifier)
}

@Composable
private fun StatsContent(
    state: StatsUiState,
    loadIcon: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val spacing = ForegroundTheme.spacing
    if (state.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item {
            Text(
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                StatCard(
                    label = stringResource(R.string.stats_unlocks),
                    value = state.unlocks.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = stringResource(R.string.stats_screen_time),
                    value = usageDurationText(state.totalMillis),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.stats_apps_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = spacing.sm),
            )
        }
        if (state.apps.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.stats_apps_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.apps, key = { it.app.packageName }) { appTime ->
                AppTimeRow(appTime = appTime, loadIcon = loadIcon, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(ForegroundTheme.spacing.md)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AppTimeRow(
    appTime: AppTime,
    loadIcon: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ForegroundTheme.spacing.md),
    ) {
        AppIcon(packageName = appTime.app.packageName, loadIcon = loadIcon)
        Text(
            text = appTime.app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = usageDurationText(appTime.millis),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** "3h 7m", "7m" or "<1m". */
@Composable
private fun usageDurationText(millis: Long): String =
    when (val duration = UsageDuration.of(millis)) {
        is UsageDuration.Hours -> stringResource(R.string.stats_duration_hours, duration.hours, duration.minutes)
        is UsageDuration.Minutes -> stringResource(R.string.stats_duration_minutes, duration.minutes)
        UsageDuration.UnderMinute -> stringResource(R.string.stats_duration_under_minute)
    }

@Preview(showBackground = true)
@Composable
private fun StatsContentPreview() {
    ForegroundTheme {
        StatsContent(
            state =
                StatsUiState(
                    loading = false,
                    unlocks = 42,
                    totalMillis = 11_400_000L,
                    apps =
                        listOf(
                            AppTime(AppInfo("com.google.android.youtube", "YouTube"), millis = 7_380_000L),
                            AppTime(AppInfo("com.whatsapp", "WhatsApp"), millis = 3_000_000L),
                            AppTime(AppInfo("com.android.chrome", "Chrome"), millis = 1_020_000L),
                        ),
                ),
            loadIcon = { null },
        )
    }
}
