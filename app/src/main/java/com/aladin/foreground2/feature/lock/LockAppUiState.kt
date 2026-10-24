package com.aladin.foreground2.feature.lock

import com.aladin.foreground2.core.domain.AppInfo

sealed interface LockAppUiState {
    /** Reading the app's label and lock. */
    data object Loading : LockAppUiState

    /** Not locked: the duration picker. */
    data class Picking(
        val app: AppInfo,
    ) : LockAppUiState

    /** Already locked until [unlockAtMillis]: read-only, a lock can't be shortened or cancelled (rule 6). */
    data class Locked(
        val app: AppInfo,
        val unlockAtMillis: Long,
    ) : LockAppUiState

    /** The lock is saved; the screen goes back to Home. */
    data object Started : LockAppUiState
}
