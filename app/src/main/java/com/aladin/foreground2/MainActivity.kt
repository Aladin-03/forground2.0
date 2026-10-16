package com.aladin.foreground2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.core.permissions.PermissionChecker
import com.aladin.foreground2.core.permissions.missingRequired
import com.aladin.foreground2.feature.home.HomeRoute
import com.aladin.foreground2.feature.onboarding.OnboardingRoute
import com.aladin.foreground2.feature.stats.StatsTab
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
            // A widget tap: Stats (on its tab) over Home, so Back lands on Home. Ignored while onboarding is needed.
            // AppNavHost opens it once and remembers that across rotation.
            val openStats =
                remember {
                    intent.getStringExtra(EXTRA_STATS_TAB)?.takeIf { !needsOnboarding }?.let(StatsTab::valueOf)
                }
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
        private const val EXTRA_STATS_TAB = "com.aladin.foreground2.STATS_TAB"

        /**
         * Starts the app afresh on the Stats screen's [tab] (over Home), on the current week with today selected.
         * Used by the home-screen widgets.
         */
        fun openStatsIntent(
            context: Context,
            tab: StatsTab,
        ): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_STATS_TAB, tab.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
