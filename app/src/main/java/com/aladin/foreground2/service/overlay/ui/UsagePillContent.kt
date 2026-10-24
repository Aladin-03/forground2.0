package com.aladin.foreground2.service.overlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.PillTokens
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.Countdown
import com.aladin.foreground2.core.ui.formatUsageDuration

private object Pill {
    const val SECONDS_PER_MINUTE = 60
}

/**
 * Rule 10: "7:42 · 1h 12m", time left until [until] (the allowed window's end, epoch ms) and today's use, both
 * ticking each second. Use is [usedToday] as read at [usageReadAt] plus the time since: the app has been in front
 * all along, or the pill would be gone. No fading or colour change near the end.
 */
@Composable
fun UsagePillContent(
    until: Long,
    usedToday: Long,
    usageReadAt: Long,
    clock: Clock,
    modifier: Modifier = Modifier,
) {
    val countdown = remember(until, clock) { Countdown(until, clock) }
    var secondsLeft by remember(countdown) { mutableIntStateOf(countdown.secondsLeftNow()) }
    var now by remember(clock) { mutableLongStateOf(clock.nowMillis()) }
    LaunchedEffect(countdown) {
        countdown.secondsLeft().collect {
            secondsLeft = it
            now = clock.nowMillis()
        }
    }
    val context = LocalContext.current
    UsagePillText(
        timeLeft =
            stringResource(
                R.string.pill_time_left,
                secondsLeft / Pill.SECONDS_PER_MINUTE,
                secondsLeft % Pill.SECONDS_PER_MINUTE,
            ),
        usage = formatUsageDuration(context, usedToday + (now - usageReadAt).coerceAtLeast(0L)),
        modifier = modifier,
    )
}

@Composable
private fun UsagePillText(
    timeLeft: String,
    usage: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.pill_text, timeLeft, usage),
        style = PillTokens.text,
        color = PillTokens.content,
        maxLines = 1,
        modifier =
            modifier
                .padding(PillTokens.shadowMargin)
                .shadow(PillTokens.elevation, PillTokens.shape)
                .background(PillTokens.background, PillTokens.shape)
                .border(PillTokens.outlineWidth, PillTokens.outline, PillTokens.shape)
                .padding(horizontal = PillTokens.horizontalPadding, vertical = PillTokens.verticalPadding),
    )
}

@Preview
@Composable
private fun UsagePillTextPreview() {
    UsagePillText(timeLeft = "7:42", usage = "1h 12m")
}
