package com.aladin.foreground2.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import kotlin.math.abs

private object Wheel {
    /** Rows visible at once; the middle one is the selection. Odd so there is a middle. */
    const val VISIBLE_ROWS = 5
}

/**
 * A vertical scroll wheel over [range] that snaps to a row. The row in the middle band is the value; it is reported
 * through [onValueChange] as the wheel moves. [initialValue] is only read on first composition.
 */
@Composable
fun NumberWheel(
    range: IntRange,
    initialValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowHeight = ForegroundTheme.sizing.wheelItemHeight
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialValue - range.first)
    val selected by remember(state) { derivedStateOf { state.centeredIndex() } }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(state, range) {
        snapshotFlow { selected }.collect { currentOnValueChange(range.first + it) }
    }

    Box(
        modifier = modifier.width(ForegroundTheme.sizing.wheelWidth).height(rowHeight * Wheel.VISIBLE_ROWS),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
        )
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
            contentPadding = PaddingValues(vertical = rowHeight * (Wheel.VISIBLE_ROWS / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count = range.count()) { index ->
                Box(modifier = Modifier.height(rowHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = (range.first + index).toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color =
                            if (index == selected) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                    )
                }
            }
        }
    }
}

/** Index of the item whose centre is nearest the viewport's centre; before the first layout, the first visible. */
private fun LazyListState.centeredIndex(): Int {
    val info = layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
        ?: firstVisibleItemIndex
}

@Preview
@Composable
private fun NumberWheelPreview() {
    ForegroundTheme { NumberWheel(range = 0..23, initialValue = 4, onValueChange = {}) }
}
