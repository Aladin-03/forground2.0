package com.aladin.foreground2.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aladin.foreground2.core.data.repo.SettingsRepository
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.core.permissions.Permission
import com.aladin.foreground2.core.permissions.PermissionChecker
import com.aladin.foreground2.service.detection.DetectionServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val permissionChecker: PermissionChecker,
        private val settingsRepository: SettingsRepository,
        private val detectionServiceController: DetectionServiceController,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OnboardingUiState())
        val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

        init {
            refresh()
        }

        /** Rechecks every permission. Called on each ON_RESUME, since grants happen in Settings. */
        fun refresh() {
            val previous = _uiState.value.permissions.associate { it.permission to it.granted }
            val current = Permission.entries.map { PermissionStatus(it, permissionChecker.isGranted(it)) }
            current
                .filter { previous[it.permission] != it.granted }
                .forEach { Log.i(Log.Area.Perm) { "${it.permission} granted=${it.granted}" } }
            _uiState.update { it.copy(permissions = current) }
        }

        fun onFinish() {
            if (!_uiState.value.canFinish) return
            viewModelScope.launch {
                settingsRepository.setOnboardingComplete()
                detectionServiceController.startIfReady()
                _uiState.update { it.copy(finished = true) }
            }
        }
    }
