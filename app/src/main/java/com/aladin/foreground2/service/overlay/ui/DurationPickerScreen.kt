package com.aladin.foreground2.service.overlay.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.LockDuration
import com.aladin.foreground2.core.ui.DurationPicker

/**
 * Rule 6 as an overlay: the shared [DurationPicker]. [onStart] gets the picked duration; the caller turns it into an
 * end time. [onBack] (only when opened with "Lock it now" during the breath) shows "Back to breath"; null hides it.
 */
@Composable
fun DurationPickerScreen(
    label: String,
    clock: Clock,
    onStart: (LockDuration) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().safeDrawingPadding().padding(ForegroundTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DurationPicker(label = label, clock = clock, onStart = onStart, modifier = Modifier.weight(1f))
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.duration_back_to_breath))
            }
        }
    }
}
