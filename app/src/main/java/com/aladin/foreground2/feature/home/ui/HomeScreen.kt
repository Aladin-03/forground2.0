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

@Composable
fun HomeScreen(
    onOpenPicker: () -> Unit,
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
        onOpenPicker = onOpenPicker,
        loadIcon = viewModel::loadIcon,
        modifier = modifier,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    clock: Clock,
    onFix: (HomeFix) -> Unit,
    onOpenPicker: () -> Unit,
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
            Button(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_open_picker))
            }
        }
        item {
            Text(
                text = stringResource(R.string.home_locks_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = spacing.sm),
            )
        }
        if (state.locks.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.home_locks_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.locks, key = { it.app.packageName }) { lock ->
                LockRow(lock = lock, clock = clock, loadIcon = loadIcon, modifier = Modifier.animateItem())
            }
        }
    }
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
                    missing = listOf(Permission.Overlay),
                    serviceRunning = true,
                ),
            clock = { 0L },
            onFix = {},
            onOpenPicker = {},
            loadIcon = { null },
        )
    }
}
