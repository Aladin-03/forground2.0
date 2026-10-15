package com.aladin.foreground2.feature.picker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import com.aladin.foreground2.feature.picker.PickerItem
import com.aladin.foreground2.feature.picker.PickerUiState
import com.aladin.foreground2.feature.picker.PickerViewModel

@Composable
fun PickerScreen(
    modifier: Modifier = Modifier,
    viewModel: PickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    PickerContent(
        state = state,
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onToggle = viewModel::onToggle,
        loadIcon = viewModel::loadIcon,
        modifier = modifier,
    )
}

@Composable
private fun PickerContent(
    state: PickerUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggle: (AppInfo, Boolean) -> Unit,
    loadIcon: suspend (String) -> ImageBitmap?,
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
            onValueChange = onQueryChange,
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
                            onToggle = onToggle,
                            loadIcon = loadIcon,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
        }
    }
}

@Composable
private fun PickerRow(
    item: PickerItem,
    onToggle: (AppInfo, Boolean) -> Unit,
    loadIcon: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val spacing = ForegroundTheme.spacing
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(
                    value = item.watched,
                    role = Role.Switch,
                    onValueChange = { onToggle(item.app, it) },
                ).padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        AppIcon(packageName = item.app.packageName, loadIcon = loadIcon)
        Text(
            text = item.app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // The row handles the toggle; the switch only shows state.
        Switch(checked = item.watched, onCheckedChange = null)
    }
}

@Composable
private fun AppIcon(
    packageName: String,
    loadIcon: suspend (String) -> ImageBitmap?,
) {
    val size = ForegroundTheme.sizing.appIcon
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) { value = loadIcon(packageName) }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size))
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
                            PickerItem(AppInfo("com.instagram.android", "Instagram"), watched = true),
                            PickerItem(AppInfo("com.android.chrome", "Chrome"), watched = false),
                            PickerItem(AppInfo("com.whatsapp", "WhatsApp"), watched = false),
                        ),
                ),
            query = "",
            onQueryChange = {},
            onToggle = { _, _ -> },
            loadIcon = { null },
        )
    }
}
