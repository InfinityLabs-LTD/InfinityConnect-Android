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
import com.infinityconnect.vpn.ui.settings.AboutScreen
import com.infinityconnect.vpn.ui.settings.AppPickerScreen
import com.infinityconnect.vpn.ui.settings.PingScreen
import com.infinityconnect.vpn.ui.settings.RoutingScreen
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
            HomeScreen(
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onOpenRouting = { navController.navigate(Routes.ROUTING) },
                onOpenPing = { navController.navigate(Routes.PING) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
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

        // Настройки — хаб-меню из отдельных пунктов.
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenRouting = { navController.navigate(Routes.ROUTING) },
                onOpenPing = { navController.navigate(Routes.PING) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }

        composable(Routes.ROUTING) { entry ->
            RoutingScreen(
                viewModel = sharedSettingsVm(navController, entry),
                onBack = { navController.popBackStack() },
                onOpenAppPicker = { navController.navigate(Routes.APP_PICKER) },
            )
        }

        composable(Routes.PING) { entry ->
            PingScreen(
                viewModel = sharedSettingsVm(navController, entry),
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.APP_PICKER) { entry ->
            AppPickerScreen(
                viewModel = sharedSettingsVm(navController, entry),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Возвращает [SettingsViewModel], привязанный к backstack-entry SETTINGS —
 * общий для всех подэкранов настроек (маршрутизация, пинг, выбор приложений).
 * Так их состояние и кэш живут в одном инстансе. Экраны открываются и напрямую
 * из бокового меню (без хаба SETTINGS в стеке) — тогда VM привязывается к HOME,
 * который жив всё время сессии, и остаётся общим между подэкранами.
 */
@Composable
private fun sharedSettingsVm(
    navController: NavHostController,
    entry: androidx.navigation.NavBackStackEntry,
): SettingsViewModel {
    val ownerEntry = androidx.compose.runtime.remember(entry) {
        runCatching { navController.getBackStackEntry(Routes.SETTINGS) }
            .getOrElse { navController.getBackStackEntry(Routes.HOME) }
    }
    return hiltViewModel(ownerEntry)
}

