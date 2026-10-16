package com.aladin.foreground2.service.detection

import com.aladin.foreground2.core.logging.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Whether [DetectionService] is running in this process. The service sets it; the home screen shows it. */
@Singleton
class DetectionStatus
    @Inject
    constructor() {
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun setRunning(running: Boolean) {
            if (_running.value == running) return
            _running.value = running
            Log.i(Log.Area.Detect) { "service running=$running" }
        }
    }
