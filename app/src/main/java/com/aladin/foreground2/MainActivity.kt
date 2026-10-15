package com.aladin.foreground2

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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var permissionChecker: PermissionChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            ForegroundTheme {
                Scaffold { innerPadding ->
                    AppNavHost(
                        startDestination = if (needsOnboarding) OnboardingRoute else HomeRoute,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
