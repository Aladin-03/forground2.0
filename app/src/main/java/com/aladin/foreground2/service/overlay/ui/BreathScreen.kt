package com.aladin.foreground2.service.overlay.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.Countdown

private object Breath {
    /** 5 s in, 5 s out: six breaths a minute. */
    const val HALF_MILLIS = 5_000
    const val CONTRACTED_SCALE = 0.55f
    const val EXPANDED_SCALE = 1f
    const val SECONDS_PER_MINUTE = 60
}

/**
 * Rule 2: a circle that expands on "Breathe in" and contracts on "Breathe out", over a countdown to [endsAt]
 * (epoch ms). The only interactive element is "Lock it now" at the bottom ([onLockNow]); there is no way to skip
 * the breath. When the countdown reaches zero, [onFinished] is called once with the time.
 */
@Composable
fun BreathScreen(
    endsAt: Long,
    clock: Clock,
    onFinished: (now: Long) -> Unit,
    onLockNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val countdown = remember(endsAt, clock) { Countdown(endsAt, clock) }
    var secondsLeft by remember(countdown) { mutableIntStateOf(countdown.secondsLeftNow()) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(countdown) {
        countdown.secondsLeft().collect { secondsLeft = it }
        currentOnFinished(clock.nowMillis())
    }
    BreathContent(secondsLeft = secondsLeft, onLockNow = onLockNow, modifier = modifier)
}

@Composable
private fun BreathContent(
    secondsLeft: Int,
    onLockNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(Breath.CONTRACTED_SCALE) }
    var inhaling by remember { mutableStateOf(true) }
    LaunchedEffect(scale) {
        val half = tween<Float>(durationMillis = Breath.HALF_MILLIS, easing = FastOutSlowInEasing)
        while (true) {
            inhaling = true
            scale.animateTo(Breath.EXPANDED_SCALE, half)
            inhaling = false
            scale.animateTo(Breath.CONTRACTED_SCALE, half)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().safeDrawingPadding().padding(ForegroundTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ForegroundTheme.spacing.xxl, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BreathCircle(scale = { scale.value }, inhaling = inhaling)
            Text(
                text =
                    stringResource(
                        R.string.breath_countdown,
                        secondsLeft / Breath.SECONDS_PER_MINUTE,
                        secondsLeft % Breath.SECONDS_PER_MINUTE,
                    ),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = stringResource(R.string.breath_lock_now_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(
            onClick = onLockNow,
            modifier = Modifier.fillMaxWidth().padding(top = ForegroundTheme.spacing.sm),
        ) {
            Text(stringResource(R.string.breath_lock_now))
        }
    }
}

/** [scale] is read in the graphics layer only, so the animation doesn't recompose. */
@Composable
private fun BreathCircle(
    scale: () -> Float,
    inhaling: Boolean,
) {
    Box(
        modifier = Modifier.size(ForegroundTheme.sizing.breathCircle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale()
                        scaleY = scale()
                    }.background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        )
        Crossfade(targetState = inhaling) { isInhaling ->
            Text(
                text = stringResource(if (isInhaling) R.string.breath_in else R.string.breath_out),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Preview
@Composable
private fun BreathContentPreview() {
    ForegroundTheme { BreathContent(secondsLeft = 42, onLockNow = {}) }
}
