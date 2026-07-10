package com.infinityconnect.vpn.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.infinityconnect.vpn.ui.SplashViewModel
import com.infinityconnect.vpn.ui.StartDestination
import com.infinityconnect.vpn.ui.auth.AuthScreen
import com.infinityconnect.vpn.ui.components.FullScreenLoading
import com.infinityconnect.vpn.ui.home.HomeScreen
import com.infinityconnect.vpn.ui.home.HomeViewModel
import com.infinityconnect.vpn.ui.onboarding.OnboardingScreen
import com.infinityconnect.vpn.ui.profile.ProfileScreen
import com.infinityconnect.vpn.ui.servers.ServersScreen

/**
 * Корневой навигационный граф. Стартовый экран (splash) определяет, куда вести:
 * онбординг / авторизация / главная. Экраны home и servers объединены во
 * вложенный граф, чтобы делить один [HomeViewModel] (выбор сервера возвращается
 * на главный экран).
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

        homeGraph(navController)

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

/** Вложенный граф home+servers с общим HomeViewModel. */
private fun NavGraphBuilder.homeGraph(navController: NavHostController) {
    navigation(startDestination = "home_main", route = Routes.HOME) {
        composable("home_main") { entry ->
            val parentEntry = remember(entry) {
                navController.getBackStackEntry(Routes.HOME)
            }
            val homeViewModel: HomeViewModel = hiltViewModel(parentEntry)
            HomeScreen(
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onOpenServers = { keyId, keyName ->
                    navController.navigate(Routes.servers(keyId, keyName))
                },
                viewModel = homeViewModel,
            )
        }

        composable(
            route = Routes.SERVERS,
            arguments = listOf(
                navArgument("keyId") { type = NavType.StringType },
                navArgument("keyName") { type = NavType.StringType },
            ),
        ) { entry ->
            val parentEntry = remember(entry) {
                navController.getBackStackEntry(Routes.HOME)
            }
            val homeViewModel: HomeViewModel = hiltViewModel(parentEntry)
            ServersScreen(
                onBack = { navController.popBackStack() },
                onServerSelected = { index, name ->
                    homeViewModel.selectServer(index, name)
                },
            )
        }
    }
}
