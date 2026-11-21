package com.aladin.foreground2.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.LockRemaining
import com.aladin.foreground2.core.domain.watch.WatchMode

private const val MINUTE = 60_000L
private const val SECOND = 1_000L

/**
 * Whose mode the chooser picks: [appLabel], currently in [current] (null when enabling), with its running lock ending
 * at [lockedUntil] (epoch ms), or null.
 */
data class ModeChooserTarget(
    val appLabel: String,
    val current: WatchMode?,
    val lockedUntil: Long? = null,
)

/** "Mindful", "Focused" or "Strict". */
@Composable
fun modeName(mode: WatchMode): String =
    stringResource(
        when (mode) {
            WatchMode.MINDFUL -> R.string.mode_mindful
            WatchMode.FOCUSED -> R.string.mode_focused
            WatchMode.STRICT -> R.string.mode_strict
        },
    )

/** A mode with its window, e.g. "Strict · 1 min" (seconds in debug builds, e.g. "Strict · 10 s"). */
@Composable
fun modeWithWindow(
    mode: WatchMode,
    windowMillis: Long,
): String {
    val window =
        if (windowMillis % MINUTE == 0L) {
            stringResource(R.string.mode_window_minutes, (windowMillis / MINUTE).toInt())
        } else {
            stringResource(R.string.mode_window_seconds, (windowMillis / SECOND).toInt())
        }
    return stringResource(R.string.mode_with_window, modeName(mode), window)
}

/**
 * Picks a watch mode for [target]. While its lock runs, the modes looser than its current one are disabled and show
 * "Locked · <time left>"; they come back by themselves when it ends. Picking an option calls [onPick]; whether it
 * applies at once or needs the sit-through countdown is the caller's business. Back, tapping outside and Cancel
 * call [onDismiss].
 */
@Composable
fun ModeChooserDialog(
    target: ModeChooserTarget,
    clock: Clock,
    windowOf: (WatchMode) -> Long,
    onPick: (WatchMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = target.current
    val remaining = target.lockedUntil?.let { rememberLockRemaining(it, clock) }
    val locked = remaining != null && remaining != LockRemaining.Ended
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mode_chooser_title, target.appLabel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ForegroundTheme.spacing.sm)) {
                Text(
                    text = stringResource(R.string.mode_chooser_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(Modifier.selectableGroup()) {
                    WatchMode.entries.forEach { mode ->
                        val blocked = locked && current != null && current.isStricterThan(mode)
                        ModeOption(
                            label = modeWithWindow(mode, windowOf(mode)),
                            note =
                                when {
                                    blocked ->
                                        stringResource(R.string.mode_locked_left, lockRemainingText(remaining))
                                    mode == current -> stringResource(R.string.mode_chooser_current)
                                    else -> null
                                },
                            selected = mode == current,
                            enabled = !blocked,
                            onClick = { onPick(mode) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.mode_chooser_cancel)) }
        },
    )
}

@Composable
private fun ModeOption(
    label: String,
    note: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val color = if (enabled) colors.onSurface else colors.onSurfaceVariant
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = ForegroundTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ForegroundTheme.spacing.sm),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = color)
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) colors.onSurfaceVariant else colors.error,
                )
            }
        }
    }
}
