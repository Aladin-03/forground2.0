package com.aladin.foreground2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aladin.foreground2.feature.home.HomeRoute
import com.aladin.foreground2.feature.home.ui.HomeScreen
import com.aladin.foreground2.feature.onboarding.OnboardingRoute
import com.aladin.foreground2.feature.onboarding.ui.OnboardingScreen
import com.aladin.foreground2.feature.picker.PickerRoute
import com.aladin.foreground2.feature.picker.ui.PickerScreen
import com.aladin.foreground2.feature.stats.StatsRoute
import com.aladin.foreground2.feature.stats.ui.StatsScreen

@Composable
fun AppNavHost(
    startDestination: Any,
    modifier: Modifier = Modifier,
    /** Opens Stats over the start destination once (saved, so it isn't repeated after rotation). */
    openStats: Boolean = false,
) {
    val navController = rememberNavController()
    var statsPending by rememberSaveable { mutableStateOf(openStats) }
    LaunchedEffect(statsPending) {
        if (statsPending) {
            navController.navigate(StatsRoute)
            statsPending = false
        }
    }
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } }
                },
            )
        }
        composable<HomeRoute> {
            HomeScreen(
                onOpenPicker = { navController.navigate(PickerRoute) },
                onOpenStats = { navController.navigate(StatsRoute) },
            )
        }
        composable<PickerRoute> { PickerScreen() }
        composable<StatsRoute> { StatsScreen() }
    }
}
