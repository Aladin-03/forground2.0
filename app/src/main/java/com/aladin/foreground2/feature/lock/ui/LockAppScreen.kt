package com.aladin.foreground2.feature.lock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.LockDuration
import com.aladin.foreground2.core.domain.LockRemaining
import com.aladin.foreground2.core.ui.AppIcon
import com.aladin.foreground2.core.ui.DurationPicker
import com.aladin.foreground2.core.ui.formatUnlockTime
import com.aladin.foreground2.core.ui.lockRemainingText
import com.aladin.foreground2.core.ui.rememberLockRemaining
import com.aladin.foreground2.feature.lock.LockAppUiState
import com.aladin.foreground2.feature.lock.LockAppViewModel

/**
 * "Lock <app>" from Home: the overlay's duration picker under the app's icon, or, if the app is already locked, its
 * lock read-only. [onDone] goes back to Home: after Start, or on Cancel / Back (which change nothing).
 */
@Composable
fun LockAppScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LockAppViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(state) { if (state == LockAppUiState.Started) currentOnDone() }
    LockAppContent(
        state = state,
        clock = viewModel.clock,
        onStart = viewModel::onStart,
        onDone = onDone,
        loadIcon = viewModel::loadIcon,
        modifier = modifier,
    )
}

@Composable
private fun LockAppContent(
    state: LockAppUiState,
    clock: Clock,
    onStart: (LockDuration) -> Unit,
    onDone: () -> Unit,
    loadIcon: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(ForegroundTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            is LockAppUiState.Picking -> {
                DurationPicker(
                    label = state.app.label,
                    clock = clock,
                    onStart = onStart,
                    modifier = Modifier.weight(1f),
                    header = { LargeIcon(state.app, loadIcon) },
                    tagline = stringResource(R.string.breath_lock_now_tagline),
                )
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.lock_app_cancel))
                }
            }
            is LockAppUiState.Locked -> {
                LockedInfo(state = state, clock = clock, loadIcon = loadIcon, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.lock_app_back))
                }
            }
            // Nothing to show while loading, or once saved and on the way back.
            LockAppUiState.Loading, LockAppUiState.Started -> Unit
        }
    }
}

@Composable
private fun LargeIcon(
    app: AppInfo,
    loadIcon: suspend (String) -> ImageBitmap?,
) {
    AppIcon(packageName = app.packageName, loadIcon = loadIcon, size = ForegroundTheme.sizing.lockIcon)
}

/** The running lock, read-only: no way to shorten or cancel it (rule 6). */
@Composable
private fun LockedInfo(
    state: LockAppUiState.Locked,
    clock: Clock,
    loadIcon: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val remaining = rememberLockRemaining(state.unlockAtMillis, clock)
    val unlockAt = remember(state.unlockAtMillis) { formatUnlockTime(context, state.unlockAtMillis) }
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ForegroundTheme.spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LargeIcon(state.app, loadIcon)
        Text(
            text = stringResource(R.string.lock_title, state.app.label),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text =
                if (remaining == LockRemaining.Ended) {
                    stringResource(R.string.lock_ended)
                } else {
                    stringResource(R.string.lock_unlocks_in, lockRemainingText(remaining))
                },
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (remaining != LockRemaining.Ended) {
            Text(
                text = stringResource(R.string.lock_unlocks_at, unlockAt),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = stringResource(R.string.lock_app_final),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LockAppPickingPreview() {
    ForegroundTheme {
        LockAppContent(
            state = LockAppUiState.Picking(AppInfo("com.google.android.youtube", "YouTube")),
            clock = { 0L },
            onStart = {},
            onDone = {},
            loadIcon = { null },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LockAppLockedPreview() {
    ForegroundTheme {
        LockAppContent(
            state =
                LockAppUiState.Locked(
                    AppInfo("com.google.android.youtube", "YouTube"),
                    unlockAtMillis = 190_000_000L,
                ),
            clock = { 0L },
            onStart = {},
            onDone = {},
            loadIcon = { null },
        )
    }
}
