package com.aladin.foreground2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.core.permissions.PermissionChecker
import com.aladin.foreground2.core.permissions.missingRequired
import com.aladin.foreground2.feature.home.HomeRoute
import com.aladin.foreground2.feature.onboarding.OnboardingRoute
import com.aladin.foreground2.service.detection.DetectionServiceController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var permissionChecker: PermissionChecker

    @Inject
    lateinit var detectionServiceController: DetectionServiceController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Brings detection back if it was stopped (force-stop, crash). Does nothing until onboarding is done.
        if (savedInstanceState == null && permissionChecker.missingRequired().isEmpty()) {
            detectionServiceController.startIfReady()
        }
        setContent {
            // Decided once per launch (saved across rotation) so the graph's start doesn't shift under the back stack.
            val needsOnboarding =
                rememberSaveable {
                    val missing = permissionChecker.missingRequired()
                    Log.i(
                        Log.Area.UI,
                    ) { "launch -> ${if (missing.isEmpty()) "Home" else "Onboarding missing=$missing"}" }
                    missing.isNotEmpty()
                }
            // A widget tap: Stats on top of Home, so Back lands on Home. Ignored while onboarding is needed.
            val openStats = rememberSaveable { !needsOnboarding && intent.getBooleanExtra(EXTRA_OPEN_STATS, false) }
            ForegroundTheme {
                Scaffold { innerPadding ->
                    AppNavHost(
                        startDestination = if (needsOnboarding) OnboardingRoute else HomeRoute,
                        openStats = openStats,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_OPEN_STATS = "com.aladin.foreground2.OPEN_STATS"

        /** Starts the app afresh on the Stats screen (over Home). Used by the home-screen widgets. */
        fun openStatsIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_STATS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
