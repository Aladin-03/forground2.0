package com.aladin.foreground2.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
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
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.LockRemaining
import com.aladin.foreground2.core.permissions.Permission
import com.aladin.foreground2.core.permissions.names
import com.aladin.foreground2.core.permissions.openSettingsFor
import com.aladin.foreground2.core.ui.AppIcon
import com.aladin.foreground2.core.ui.formatUnlockTime
import com.aladin.foreground2.core.ui.lockRemainingText
import com.aladin.foreground2.core.ui.rememberLockRemaining
import com.aladin.foreground2.feature.home.HomeFix
import com.aladin.foreground2.feature.home.HomeUiState
import com.aladin.foreground2.feature.home.HomeViewModel
import com.aladin.foreground2.feature.home.LockedApp
import com.aladin.foreground2.feature.home.WatchedApp

// Lazy-list keys are per section, since an app can be in both lists.
private enum class Section { Locked, Watched }

/** Screens Home links to. */
private enum class HomeLink { Picker, Stats }

@Composable
fun HomeScreen(
    onOpenPicker: () -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    HomeContent(
        state = state,
        clock = viewModel.clock,
        onFix = { fix ->
            when (fix) {
                is HomeFix.Grant -> context.openSettingsFor(fix.permission)
                HomeFix.StartService -> viewModel.onStartService()
            }
        },
        onOpen = { link ->
            when (link) {
                HomeLink.Picker -> onOpenPicker()
                HomeLink.Stats -> onOpenStats()
            }
        },
        loadIcon = viewModel::loadIcon,
        modifier = modifier,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    clock: Clock,
    onFix: (HomeFix) -> Unit,
    onOpen: (HomeLink) -> Unit,
    loadIcon: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val spacing = ForegroundTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item { StatusCard(state = state, onFix = onFix) }
        item {
            Button(onClick = { onOpen(HomeLink.Picker) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_open_picker))
            }
        }
        item {
            OutlinedButton(onClick = { onOpen(HomeLink.Stats) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_open_stats))
            }
        }
        item { SectionTitle(stringResource(R.string.home_locks_title)) }
        if (state.locks.isEmpty()) {
            item { EmptyText(stringResource(R.string.home_locks_empty)) }
        } else {
            items(state.locks, key = { Section.Locked to it.app.packageName }) { lock ->
                LockRow(lock = lock, clock = clock, loadIcon = loadIcon, modifier = Modifier.animateItem())
            }
        }
        item { SectionTitle(stringResource(R.string.home_watched_title)) }
        if (state.watched.isEmpty()) {
            item { EmptyText(stringResource(R.string.home_watched_empty)) }
        } else {
            items(state.watched, key = { Section.Watched to it.app.packageName }) { watched ->
                WatchedRow(watched = watched, loadIcon = loadIcon, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = ForegroundTheme.spacing.sm),
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Whether detection can work right now, with one button for the most urgent fix. */
@Composable
private fun StatusCard(
    state: HomeUiState,
    onFix: (HomeFix) -> Unit,
) {
    val spacing = ForegroundTheme.spacing
    val fix = state.fix
    val text =
        when {
            state.missingRequired.isNotEmpty() ->
                stringResource(R.string.home_status_missing, state.missingRequired.names(LocalContext.current))
            !state.serviceRunning -> stringResource(R.string.home_status_stopped)
            state.missing.isNotEmpty() -> stringResource(R.string.home_status_battery)
            else -> stringResource(R.string.home_status_ok)
        }
    val ok = fix == null
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            if (fix != null) {
                OutlinedButton(onClick = { onFix(fix) }) {
                    Text(
                        stringResource(if (fix == HomeFix.StartService) R.string.home_start else R.string.home_fix),
                    )
                }
            }
        }
    }
}

@Composable
private fun LockRow(
    lock: LockedApp,
    clock: Clock,
    loadIcon: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val spacing = ForegroundTheme.spacing
    val context = LocalContext.current
    val remaining = rememberLockRemaining(lock.unlockAtMillis, clock)
    val unlockAt = remember(lock.unlockAtMillis) { formatUnlockTime(context, lock.unlockAtMillis) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        AppIcon(packageName = lock.app.packageName, loadIcon = loadIcon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = lock.app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.lock_unlocks_at, unlockAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text =
                if (remaining == LockRemaining.Ended) {
                    stringResource(R.string.lock_ended)
                } else {
                    lockRemainingText(remaining)
                },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A watched app, marked locked or breath-on-open. */
@Composable
private fun WatchedRow(
    watched: WatchedApp,
    loadIcon: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ForegroundTheme.spacing.md),
    ) {
        AppIcon(packageName = watched.app.packageName, loadIcon = loadIcon)
        Text(
            text = watched.app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(if (watched.locked) R.string.home_watched_locked else R.string.home_watched_breath),
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (watched.locked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    ForegroundTheme {
        HomeContent(
            state =
                HomeUiState(
                    locks =
                        listOf(
                            LockedApp(AppInfo("com.instagram.android", "Instagram"), unlockAtMillis = 2_700_000L),
                            LockedApp(AppInfo("com.google.android.youtube", "YouTube"), unlockAtMillis = 190_000_000L),
                        ),
                    watched =
                        listOf(
                            WatchedApp(AppInfo("com.instagram.android", "Instagram"), locked = true),
                            WatchedApp(AppInfo("com.whatsapp", "WhatsApp"), locked = false),
                            WatchedApp(AppInfo("com.google.android.youtube", "YouTube"), locked = true),
                        ),
                    missing = listOf(Permission.Overlay),
                    serviceRunning = true,
                ),
            clock = { 0L },
            onFix = {},
            onOpen = {},
            loadIcon = { null },
        )
    }
}
