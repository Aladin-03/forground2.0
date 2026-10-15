package com.aladin.foreground2

import androidx.compose.runtime.Composable
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

@Composable
fun AppNavHost(
    startDestination: Any,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } }
                },
            )
        }
        composable<HomeRoute> { HomeScreen(onOpenPicker = { navController.navigate(PickerRoute) }) }
        composable<PickerRoute> { PickerScreen() }
    }
}
