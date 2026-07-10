package com.infinityconnect.vpn.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.infinityconnect.vpn.ui.SplashViewModel
import com.infinityconnect.vpn.ui.StartDestination
import com.infinityconnect.vpn.ui.auth.AuthScreen
import com.infinityconnect.vpn.ui.components.FullScreenLoading
import com.infinityconnect.vpn.ui.home.HomeScreen
import com.infinityconnect.vpn.ui.onboarding.OnboardingScreen
import com.infinityconnect.vpn.ui.profile.ProfileScreen

/**
 * Корневой навигационный граф. Стартовый экран (splash) определяет, куда вести:
 * онбординг / авторизация / главная. Серверы подписки раскрываются прямо на
 * главном экране (стиль Happ), отдельного экрана серверов нет.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            val viewModel: SplashViewModel = hiltViewModel()
            val destination by viewModel.destination.collectAsStateWithLifecycle()
            FullScreenLoading()
            androidx.compose.runtime.LaunchedEffect(destination) {
                val dest = destination ?: return@LaunchedEffect
                val route = when (dest) {
                    StartDestination.ONBOARDING -> Routes.ONBOARDING
                    StartDestination.AUTH -> Routes.AUTH
                    StartDestination.HOME -> Routes.HOME
                }
                navController.navigate(route) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDiscovered = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.AUTH) {
            AuthScreen(
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}

