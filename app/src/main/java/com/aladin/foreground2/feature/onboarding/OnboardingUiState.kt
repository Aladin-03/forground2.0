package com.aladin.foreground2.feature.onboarding

import com.aladin.foreground2.core.permissions.Permission

data class PermissionStatus(
    val permission: Permission,
    val granted: Boolean,
)

data class OnboardingUiState(
    val permissions: List<PermissionStatus> = emptyList(),
    /** Set once the user finished onboarding and the flag is persisted; the screen navigates on it. */
    val finished: Boolean = false,
) {
    val canFinish: Boolean
        get() = permissions.isNotEmpty() && permissions.all { it.granted || !it.permission.required }
}
