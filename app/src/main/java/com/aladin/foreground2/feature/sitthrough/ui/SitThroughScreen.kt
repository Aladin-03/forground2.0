package com.aladin.foreground2.feature.sitthrough.ui

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.Countdown
import com.aladin.foreground2.core.domain.watch.WatchMode
import com.aladin.foreground2.core.ui.AppIcon
import com.aladin.foreground2.core.ui.modeName
import com.aladin.foreground2.feature.sitthrough.SitThroughUiState
import com.aladin.foreground2.feature.sitthrough.SitThroughViewModel
import kotlin.time.Duration.Companion.seconds

/**
 * The sit-through countdown, full screen: what will change, the time left, and Cancel. The screen stays on while it
 * runs. Leaving it any way (Back, Cancel, Home, another app, screen off) cancels it and [onDone] goes back; so does
 * finishing, after the change is applied. A rotation is not leaving.
 */
@Composable
fun SitThroughScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SitThroughViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    // Cancel and close straight from the lifecycle callback: recomposition doesn't run while stopped, and the back
    // stack saved for a later process death must no longer hold this screen.
    DisposableEffect(lifecycleOwner, activity) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) {
                    viewModel.onLeave(SitThroughViewModel.LeaveReason.LeftScreen)
                    currentOnDone()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler { viewModel.onLeave(SitThroughViewModel.LeaveReason.Back) }
    LaunchedEffect(state) { if (state == SitThroughUiState.Finished) currentOnDone() }

    SitThroughContent(
        state = state,
        clock = viewModel.clock,
        onCancel = { viewModel.onLeave(SitThroughViewModel.LeaveReason.Cancel) },
        loadIcon = viewModel::loadIcon,
        modifier = modifier,
    )
}

@Composable
private fun SitThroughContent(
    state: SitThroughUiState,
    clock: Clock,
    onCancel: () -> Unit,
    loadIcon: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val spacing = ForegroundTheme.spacing
    Column(
        modifier = modifier.fillMaxSize().padding(spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state is SitThroughUiState.Running) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.lg, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(packageName = state.packageName, loadIcon = loadIcon, size = ForegroundTheme.sizing.lockIcon)
                Text(
                    text =
                        if (state.to == null) {
                            stringResource(R.string.sit_disable_title, state.label)
                        } else {
                            stringResource(R.string.sit_loosen_title, state.label, modeName(state.to))
                        },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                CountdownText(endsAt = state.endsAt, clock = clock)
                Text(
                    text = stringResource(R.string.sit_think),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sit_cancel))
            }
        }
    }
}

/** "4:59", ticking at each second boundary. */
@Composable
private fun CountdownText(
    endsAt: Long,
    clock: Clock,
) {
    val countdown = remember(endsAt, clock) { Countdown(endsAt, clock) }
    var secondsLeft by remember(countdown) { mutableIntStateOf(countdown.secondsLeftNow()) }
    LaunchedEffect(countdown) { countdown.secondsLeft().collect { secondsLeft = it } }
    Text(
        text =
            secondsLeft.seconds.toComponents { minutes, seconds, _ ->
                stringResource(R.string.sit_countdown, minutes.toInt(), seconds)
            },
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
}

@Preview(showBackground = true)
@Composable
private fun SitThroughLoosenPreview() {
    ForegroundTheme {
        SitThroughContent(
            state = SitThroughUiState.Running("com.google.android.youtube", "YouTube", WatchMode.MINDFUL, endsAt = 0L),
            clock = { 0L },
            onCancel = {},
            loadIcon = { null },
        )
    }
}
