package com.aladin.foreground2.service.overlay.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.ForegroundTheme

/** Rule 4: the app's icon and label, "Block this app?", and Yes / No. */
@Composable
fun PromptScreen(
    label: String,
    loadIcon: suspend () -> ImageBitmap?,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon by produceState<ImageBitmap?>(initialValue = null, loadIcon) { value = loadIcon() }
    PromptContent(label = label, icon = icon, onYes = onYes, onNo = onNo, modifier = modifier)
}

@Composable
private fun PromptContent(
    label: String,
    icon: ImageBitmap?,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().safeDrawingPadding().padding(ForegroundTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(ForegroundTheme.spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Fixed-size slot so the layout doesn't jump when the icon arrives.
        Box(modifier = Modifier.size(ForegroundTheme.sizing.promptIcon), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.prompt_question),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = ForegroundTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(ForegroundTheme.spacing.md),
        ) {
            OutlinedButton(onClick = onNo, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.prompt_no))
            }
            Button(onClick = onYes, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.prompt_yes))
            }
        }
    }
}

@Preview
@Composable
private fun PromptContentPreview() {
    ForegroundTheme { PromptContent(label = "YouTube", icon = null, onYes = {}, onNo = {}) }
}
