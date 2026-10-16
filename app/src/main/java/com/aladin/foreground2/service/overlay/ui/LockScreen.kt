package com.aladin.foreground2.service.overlay.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.LockRemaining
import com.aladin.foreground2.core.ui.formatUnlockTime
import com.aladin.foreground2.core.ui.lockRemainingText
import com.aladin.foreground2.core.ui.rememberLockRemaining

/**
 * Rule 7: the app's icon and label, a live "Unlocks in ..." countdown to [until] (epoch ms), the exact unlock time,
 * and Go home, the only way out. If the lock ends while showing, the screen says so and still only offers Go home;
 * the next entry breathes.
 */
@Composable
fun LockScreen(
    label: String,
    until: Long,
    clock: Clock,
    loadIcon: suspend () -> ImageBitmap?,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon by produceState<ImageBitmap?>(initialValue = null, loadIcon) { value = loadIcon() }
    val context = LocalContext.current
    LockContent(
        label = label,
        icon = icon,
        remaining = rememberLockRemaining(until, clock),
        unlockAt = remember(until) { formatUnlockTime(context, until) },
        onGoHome = onGoHome,
        modifier = modifier,
    )
}

@Composable
private fun LockContent(
    label: String,
    icon: ImageBitmap?,
    remaining: LockRemaining,
    unlockAt: String,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().safeDrawingPadding().padding(ForegroundTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(ForegroundTheme.spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Fixed-size slot so the layout doesn't jump when the icon arrives.
        Box(modifier = Modifier.size(ForegroundTheme.sizing.lockIcon), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            text = stringResource(R.string.lock_title, label),
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
        Text(
            text =
                if (remaining == LockRemaining.Ended) {
                    stringResource(R.string.lock_ended_hint)
                } else {
                    stringResource(R.string.lock_unlocks_at, unlockAt)
                },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth().padding(top = ForegroundTheme.spacing.lg)) {
            Text(stringResource(R.string.lock_go_home))
        }
    }
}

@Preview
@Composable
private fun LockContentPreview() {
    ForegroundTheme {
        LockContent(
            label = "YouTube",
            icon = null,
            remaining = LockRemaining.Days(days = 2, hours = 3, minutes = 4),
            unlockAt = "Fri 17 Oct, 14:30",
            onGoHome = {},
        )
    }
}

@Preview
@Composable
private fun LockContentEndedPreview() {
    ForegroundTheme {
        LockContent(
            label = "YouTube",
            icon = null,
            remaining = LockRemaining.Ended,
            unlockAt = "Fri 17 Oct, 14:30",
            onGoHome = {},
        )
    }
}
