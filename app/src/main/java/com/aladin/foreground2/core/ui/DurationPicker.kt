package com.aladin.foreground2.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.LockDuration
import kotlinx.coroutines.delay

private object Picker {
    const val PREVIEW_REFRESH_MILLIS = 1_000L
}

/**
 * Rule 6, shared by the overlay picker and Home's "Lock <app>" screen: wheels for days, hours and minutes, a live
 * "Unlocks at" preview, and Start (disabled at zero). [onStart] gets the picked duration; the caller turns it into an
 * end time. [header] goes above the title and [tagline] just above Start; the overlay uses neither.
 */
@Composable
fun DurationPicker(
    label: String,
    clock: Clock,
    onStart: (LockDuration) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    tagline: String? = null,
) {
    var duration by remember { mutableStateOf(LockDuration.Zero) }
    // Re-read so the preview keeps up with the clock while the user is deciding.
    val now by produceState(clock.nowMillis(), clock) {
        while (true) {
            delay(Picker.PREVIEW_REFRESH_MILLIS)
            value = clock.nowMillis()
        }
    }
    val context = LocalContext.current
    DurationPickerContent(
        label = label,
        duration = duration,
        unlockAt = if (duration.isZero) null else formatUnlockTime(context, now + duration.millis),
        onDurationChange = { duration = it },
        onStart = { onStart(duration) },
        modifier = modifier,
        header = header,
        tagline = tagline,
    )
}

@Composable
private fun DurationPickerContent(
    label: String,
    duration: LockDuration,
    unlockAt: String?,
    onDurationChange: (LockDuration) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    tagline: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ForegroundTheme.spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        header?.invoke()
        Text(
            text = stringResource(R.string.duration_title, label),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        DurationWheels(duration = duration, onDurationChange = onDurationChange)
        Text(
            text =
                unlockAt?.let { stringResource(R.string.duration_unlocks_at, it) }
                    ?: stringResource(R.string.duration_pick_hint),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.duration_final),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
        if (tagline != null) {
            Text(
                text = tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
        Button(
            onClick = onStart,
            enabled = !duration.isZero,
            modifier = Modifier.fillMaxWidth().padding(top = ForegroundTheme.spacing.md),
        ) {
            Text(stringResource(R.string.duration_start))
        }
    }
}

@Composable
private fun DurationWheels(
    duration: LockDuration,
    onDurationChange: (LockDuration) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ForegroundTheme.spacing.md)) {
        LabelledWheel(
            range = LockDuration.DAYS,
            initialValue = duration.days,
            unit = stringResource(R.string.duration_days),
            onValueChange = { onDurationChange(duration.copy(days = it)) },
        )
        LabelledWheel(
            range = LockDuration.HOURS,
            initialValue = duration.hours,
            unit = stringResource(R.string.duration_hours),
            onValueChange = { onDurationChange(duration.copy(hours = it)) },
        )
        LabelledWheel(
            range = LockDuration.MINUTES,
            initialValue = duration.minutes,
            unit = stringResource(R.string.duration_minutes),
            onValueChange = { onDurationChange(duration.copy(minutes = it)) },
        )
    }
}

@Composable
private fun LabelledWheel(
    range: IntRange,
    initialValue: Int,
    unit: String,
    onValueChange: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        NumberWheel(range = range, initialValue = initialValue, onValueChange = onValueChange)
        Text(
            text = unit,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = ForegroundTheme.spacing.sm),
        )
    }
}

@Preview
@Composable
private fun DurationPickerContentPreview() {
    ForegroundTheme {
        DurationPickerContent(
            label = "YouTube",
            duration = LockDuration(days = 0, hours = 2, minutes = 30),
            unlockAt = "Fri 17 Oct, 14:30",
            onDurationChange = {},
            onStart = {},
        )
    }
}
