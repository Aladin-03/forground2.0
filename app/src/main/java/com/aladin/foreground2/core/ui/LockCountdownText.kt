package com.aladin.foreground2.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.aladin.foreground2.R
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.Countdown
import com.aladin.foreground2.core.domain.LockRemaining

/** Time left until [until] (epoch ms) on [clock], recomposing at each second boundary while shown. */
@Composable
fun rememberLockRemaining(
    until: Long,
    clock: Clock,
): LockRemaining {
    val countdown = remember(until, clock) { Countdown(until, clock) }
    var secondsLeft by remember(countdown) { mutableIntStateOf(countdown.secondsLeftNow()) }
    LaunchedEffect(countdown) { countdown.secondsLeft().collect { secondsLeft = it } }
    return LockRemaining.of(secondsLeft)
}

/** "2d 3h 4m", "3h 4m" or "4m 05s"; empty once the lock has ended. */
@Composable
fun lockRemainingText(remaining: LockRemaining): String =
    when (remaining) {
        is LockRemaining.Days ->
            stringResource(R.string.lock_remaining_days, remaining.days, remaining.hours, remaining.minutes)
        is LockRemaining.Hours -> stringResource(R.string.lock_remaining_hours, remaining.hours, remaining.minutes)
        is LockRemaining.Minutes ->
            stringResource(R.string.lock_remaining_minutes, remaining.minutes, remaining.seconds)
        LockRemaining.Ended -> ""
    }
