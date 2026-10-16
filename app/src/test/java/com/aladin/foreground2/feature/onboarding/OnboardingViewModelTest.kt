package com.aladin.foreground2.feature.onboarding

import com.aladin.foreground2.core.data.repo.SettingsRepository
import com.aladin.foreground2.core.permissions.Permission
import com.aladin.foreground2.core.permissions.PermissionChecker
import com.aladin.foreground2.core.permissions.missingRequired
import com.aladin.foreground2.service.detection.DetectionServiceController
import com.aladin.foreground2.testing.MainDispatcherExtension
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private class FakePermissionChecker : PermissionChecker {
    val granted = mutableSetOf<Permission>()

    override fun isGranted(permission: Permission): Boolean = permission in granted
}

private class FakeSettingsRepository : SettingsRepository {
    override val onboardingComplete = MutableStateFlow(false)

    override suspend fun setOnboardingComplete() {
        onboardingComplete.value = true
    }

    override suspend fun mediaMutedByApp() = false

    override suspend fun setMediaMutedByApp(muted: Boolean) = Unit
}

private class FakeDetectionServiceController : DetectionServiceController {
    override val running = MutableStateFlow(false)
    var starts = 0

    override fun startIfReady(): Boolean {
        starts++
        return true
    }
}

class OnboardingViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val checker = FakePermissionChecker()
    private val settings = FakeSettingsRepository()
    private val detection = FakeDetectionServiceController()
    private val viewModel = OnboardingViewModel(checker, settings, detection)

    private fun grantedIn(state: OnboardingUiState) = state.permissions.filter { it.granted }.map { it.permission }

    @Test
    fun `lists every permission, none granted initially`() {
        val state = viewModel.uiState.value
        assertEquals(Permission.entries, state.permissions.map { it.permission })
        assertTrue(grantedIn(state).isEmpty())
        assertFalse(state.canFinish)
    }

    @Test
    fun `refresh picks up grants made in Settings`() {
        checker.granted += Permission.Overlay
        viewModel.refresh()
        assertEquals(listOf(Permission.Overlay), grantedIn(viewModel.uiState.value))
    }

    @Test
    fun `can finish with only the required permissions, battery is optional`() {
        checker.granted += listOf(Permission.UsageAccess, Permission.Overlay)
        viewModel.refresh()
        assertFalse(viewModel.uiState.value.canFinish)

        checker.granted += Permission.Notifications
        viewModel.refresh()
        assertTrue(viewModel.uiState.value.canFinish)
        assertTrue(checker.missingRequired().isEmpty())
    }

    @Test
    fun `finish persists the flag and starts detection once required permissions are granted`() {
        viewModel.onFinish()
        assertFalse(viewModel.uiState.value.finished)
        assertFalse(settings.onboardingComplete.value)
        assertEquals(0, detection.starts)

        checker.granted += Permission.entries.filter { it.required }
        viewModel.refresh()
        viewModel.onFinish()
        assertTrue(viewModel.uiState.value.finished)
        assertTrue(settings.onboardingComplete.value)
        assertEquals(1, detection.starts)
    }
}
