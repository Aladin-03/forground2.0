package com.aladin.foreground2.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.aladin.foreground2.core.designsystem.ForegroundTheme

/** An app's launcher icon in a list row, loaded off the main thread. Keeps its space while loading. */
@Composable
fun AppIcon(
    packageName: String,
    loadIcon: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val size = ForegroundTheme.sizing.appIcon
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) { value = loadIcon(packageName) }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size))
    }
}
