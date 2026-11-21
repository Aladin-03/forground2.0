package com.aladin.foreground2.feature.picker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.LockRemaining
import com.aladin.foreground2.core.domain.watch.WatchMode
import com.aladin.foreground2.core.ui.AppIcon
import com.aladin.foreground2.core.ui.ModeChooserDialog
import com.aladin.foreground2.core.ui.ModeChooserTarget
import com.aladin.foreground2.core.ui.lockRemainingText
import com.aladin.foreground2.core.ui.modeWithWindow
import com.aladin.foreground2.core.ui.rememberLockRemaining
import com.aladin.foreground2.feature.picker.PickerItem
import com.aladin.foreground2.feature.picker.PickerUiState
import com.aladin.foreground2.feature.picker.PickerViewModel
import com.aladin.foreground2.feature.sitthrough.SitThroughRoute

/** What the picker's rows and search field call back into. */
private class PickerActions(
    val windowOf: (WatchMode) -> Long,
    val onQueryChange: (String) -> Unit,
    val onToggle: (AppInfo, Boolean) -> Unit,
    val loadIcon: suspend (String) -> ImageBitmap?,
)

/**
 * Switching an app on opens the mode chooser, and only a picked mode enables it (dismissing leaves it off).
 * Switching it off opens the sit-through countdown via [onOpenSitThrough]. A locked watched app's switch is disabled.
 */
@Composable
fun PickerScreen(
    onOpenSitThrough: (SitThroughRoute) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val currentOnOpenSitThrough by rememberUpdatedState(onOpenSitThrough)
    LaunchedEffect(viewModel) { viewModel.sitThrough.collect { currentOnOpenSitThrough(it) } }
    // The app whose switch was turned on, while its mode chooser is open.
    var choosingFor by rememberSaveable { mutableStateOf<String?>(null) }
    PickerContent(
        state = state,
        query = query,
        clock = viewModel.clock,
        actions =
            remember(viewModel) {
                PickerActions(
                    windowOf = viewModel::windowMillis,
                    onQueryChange = viewModel::onQueryChange,
                    onToggle = { app, on -> if (on) choosingFor = app.packageName else viewModel.onDisable(app) },
                    loadIcon = viewModel::loadIcon,
                )
            },
        modifier = modifier,
    )
    val choosing = state.items.find { it.app.packageName == choosingFor }
    if (choosing != null) {
        ModeChooserDialog(
            target = ModeChooserTarget(choosing.app.label, current = null),
            clock = viewModel.clock,
            windowOf = viewModel::windowMillis,
            onPick = { mode ->
                viewModel.onEnable(choosing.app, mode)
                choosingFor = null
            },
            onDismiss = { choosingFor = null },
        )
    }
}

@Composable
private fun PickerContent(
    state: PickerUiState,
    query: String,
    clock: Clock,
    actions: PickerActions,
    modifier: Modifier = Modifier,
) {
    val spacing = ForegroundTheme.spacing
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.picker_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.md),
        )
        OutlinedTextField(
            value = query,
            onValueChange = actions.onQueryChange,
            placeholder = { Text(stringResource(R.string.picker_search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md),
        )
        when {
            state.loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.items.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(spacing.lg), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.picker_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = spacing.sm),
                ) {
                    items(state.items, key = { it.app.packageName }) { item ->
                        PickerRow(
                            item = item,
                            clock = clock,
                            actions = actions,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
        }
    }
}

/**
 * One app with its switch. A watched app also shows its mode; while it is locked the switch is disabled and the row
 * shows "Locked · <time left>", until the lock ends.
 */
@Composable
private fun PickerRow(
    item: PickerItem,
    clock: Clock,
    actions: PickerActions,
    modifier: Modifier = Modifier,
) {
    val spacing = ForegroundTheme.spacing
    val remaining = if (item.watched) item.lockedUntil?.let { rememberLockRemaining(it, clock) } else null
    val locked = remaining != null && remaining != LockRemaining.Ended
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(
                    value = item.watched,
                    enabled = !locked,
                    role = Role.Switch,
                    onValueChange = { actions.onToggle(item.app, it) },
                ).padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        AppIcon(packageName = item.app.packageName, loadIcon = actions.loadIcon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.mode != null) {
                Text(
                    text = modeWithWindow(item.mode, actions.windowOf(item.mode)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (locked) {
                Text(
                    text = stringResource(R.string.mode_locked_left, lockRemainingText(remaining)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        // The row handles the toggle; the switch only shows state.
        Switch(checked = item.watched, onCheckedChange = null, enabled = !locked)
    }
}

@Preview(showBackground = true)
@Composable
private fun PickerContentPreview() {
    ForegroundTheme {
        PickerContent(
            state =
                PickerUiState(
                    loading = false,
                    items =
                        listOf(
                            PickerItem(
                                AppInfo("com.instagram.android", "Instagram"),
                                mode = WatchMode.STRICT,
                                lockedUntil = 2_700_000L,
                            ),
                            PickerItem(AppInfo("com.google.android.youtube", "YouTube"), mode = WatchMode.MINDFUL),
                            PickerItem(AppInfo("com.android.chrome", "Chrome")),
                            PickerItem(AppInfo("com.whatsapp", "WhatsApp")),
                        ),
                ),
            query = "",
            clock = { 0L },
            actions =
                PickerActions(windowOf = { 600_000L }, onQueryChange = {}, onToggle = {
                    _,
                    _,
                    ->
                }, loadIcon = { null }),
        )
    }
}
