package com.aladin.foreground2.service.overlay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.service.overlay.formatUnlockTime

/**
 * Stand-in for the rule 7 lock screen until it is built: the app's label and when it unlocks ([until], epoch ms).
 * No buttons; Home leaves.
 */
@Composable
fun LockedPlaceholderScreen(
    label: String,
    until: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LockedPlaceholderContent(label = label, unlockAt = remember(until) { formatUnlockTime(context, until) }, modifier)
}

@Composable
private fun LockedPlaceholderContent(
    label: String,
    unlockAt: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().safeDrawingPadding().padding(ForegroundTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(ForegroundTheme.spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.locked_placeholder_title, label),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.locked_placeholder_until, unlockAt),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
private fun LockedPlaceholderContentPreview() {
    ForegroundTheme { LockedPlaceholderContent(label = "YouTube", unlockAt = "Fri 17 Oct, 14:30") }
}
