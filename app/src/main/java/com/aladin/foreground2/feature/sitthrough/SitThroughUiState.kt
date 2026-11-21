package com.aladin.foreground2.feature.sitthrough

import com.aladin.foreground2.core.domain.watch.WatchMode

sealed interface SitThroughUiState {
    data object Loading : SitThroughUiState

    /** Counting down to [endsAt] (epoch ms) before [packageName] is unwatched ([to] null) or loosened to [to]. */
    data class Running(
        val packageName: String,
        val label: String,
        val to: WatchMode?,
        val endsAt: Long,
    ) : SitThroughUiState

    /** Applied, cancelled or refused: the screen closes. */
    data object Finished : SitThroughUiState
}
