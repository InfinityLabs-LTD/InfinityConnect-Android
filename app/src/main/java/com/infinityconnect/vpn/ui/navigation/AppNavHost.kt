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
import com.infinityconnect.vpn.ui.components.FullScreenMessage
import com.infinityconnect.vpn.ui.home.HomeScreen
import com.infinityconnect.vpn.ui.profile.ProfileScreen
import com.infinityconnect.vpn.ui.settings.AppPickerScreen
import com.infinityconnect.vpn.ui.settings.SettingsScreen
import com.infinityconnect.vpn.ui.settings.SettingsViewModel

/**
 * Корневой навигационный граф. Домен сервера фиксирован, экрана онбординга нет:
 * splash сам выполняет discovery и ведёт на авторизацию или главную. Серверы
 * подписки раскрываются на главном экране (стиль Happ).
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            val viewModel: SplashViewModel = hiltViewModel()
            val destination by viewModel.destination.collectAsStateWithLifecycle()

            when (destination) {
                StartDestination.ERROR ->
                    FullScreenMessage(
                        title = "Нет соединения с сервером",
                        description = "Проверьте интернет и попробуйте снова.",
                        actionLabel = "Повторить",
                        onAction = { viewModel.retry() },
                    )
                else -> FullScreenLoading()
            }

            androidx.compose.runtime.LaunchedEffect(destination) {
                val route = when (destination) {
                    StartDestination.AUTH -> Routes.AUTH
                    StartDestination.HOME -> Routes.HOME
                    else -> return@LaunchedEffect // null (загрузка) или ERROR
                }
                navController.navigate(route) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
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
            val toAuth: () -> Unit = {
                navController.navigate(Routes.AUTH) {
                    popUpTo(0) { inclusive = true }
                }
            }
            HomeScreen(
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onLogout = toAuth,
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

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAppPicker = { navController.navigate(Routes.APP_PICKER) },
            )
        }

        composable(Routes.APP_PICKER) { entry ->
            // Общий ViewModel с экраном настроек (scope — backstack-entry SETTINGS),
            // чтобы выбор приложений и переключатели жили в одном состоянии.
            val settingsEntry = androidx.compose.runtime.remember(entry) {
                navController.getBackStackEntry(Routes.SETTINGS)
            }
            val vm: SettingsViewModel = hiltViewModel(settingsEntry)
            AppPickerScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

