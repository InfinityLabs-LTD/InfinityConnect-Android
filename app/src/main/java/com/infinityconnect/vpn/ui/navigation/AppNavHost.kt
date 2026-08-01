package com.infinityconnect.vpn.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.res.stringResource
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.ui.SplashViewModel
import com.infinityconnect.vpn.ui.StartDestination
import com.infinityconnect.vpn.ui.auth.AuthScreen
import com.infinityconnect.vpn.ui.components.FullScreenLoading
import com.infinityconnect.vpn.ui.components.FullScreenMessage
import com.infinityconnect.vpn.ui.home.HomeScreen
import com.infinityconnect.vpn.ui.profile.ProfileScreen
import com.infinityconnect.vpn.ui.settings.AboutScreen
import com.infinityconnect.vpn.ui.settings.AppPickerScreen
import com.infinityconnect.vpn.ui.settings.LanguageScreen
import com.infinityconnect.vpn.ui.settings.LogsScreen
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
                        title = stringResource(R.string.splash_no_connection_title),
                        description = stringResource(R.string.splash_no_connection_description),
                        actionLabel = stringResource(R.string.common_retry),
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

        composable(Routes.HOME) { entry ->
            HomeScreen(
                onOpenProfile = { navController.navigateOnce(entry, Routes.PROFILE) },
                onOpenRouting = { navController.navigateOnce(entry, Routes.ROUTING) },
                onOpenPing = { navController.navigateOnce(entry, Routes.PING) },
                onOpenLogs = { navController.navigateOnce(entry, Routes.LOGS) },
                onOpenLanguage = { navController.navigateOnce(entry, Routes.LANGUAGE) },
                onOpenAbout = { navController.navigateOnce(entry, Routes.ABOUT) },
            )
        }

        composable(Routes.PROFILE) { entry ->
            ProfileScreen(
                onBack = { navController.popOnce(entry) },
                onLoggedOut = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // Настройки — хаб-меню из отдельных пунктов.
        composable(Routes.SETTINGS) { entry ->
            SettingsScreen(
                onBack = { navController.popOnce(entry) },
                onOpenRouting = { navController.navigateOnce(entry, Routes.ROUTING) },
                onOpenPing = { navController.navigateOnce(entry, Routes.PING) },
                onOpenLogs = { navController.navigateOnce(entry, Routes.LOGS) },
                onOpenLanguage = { navController.navigateOnce(entry, Routes.LANGUAGE) },
                onOpenAbout = { navController.navigateOnce(entry, Routes.ABOUT) },
            )
        }

        composable(Routes.ROUTING) { entry ->
            RoutingScreen(
                viewModel = sharedSettingsVm(navController, entry),
                onBack = { navController.popOnce(entry) },
                onOpenAppPicker = { navController.navigateOnce(entry, Routes.APP_PICKER) },
            )
        }

        composable(Routes.PING) { entry ->
            PingScreen(
                viewModel = sharedSettingsVm(navController, entry),
                onBack = { navController.popOnce(entry) },
            )
        }

        composable(Routes.ABOUT) { entry ->
            AboutScreen(onBack = { navController.popOnce(entry) })
        }

        composable(Routes.LOGS) { entry ->
            LogsScreen(onBack = { navController.popOnce(entry) })
        }

        composable(Routes.LANGUAGE) { entry ->
            LanguageScreen(
                viewModel = sharedSettingsVm(navController, entry),
                onBack = { navController.popOnce(entry) },
            )
        }

        composable(Routes.APP_PICKER) { entry ->
            AppPickerScreen(
                viewModel = sharedSettingsVm(navController, entry),
                onBack = { navController.popOnce(entry) },
            )
        }
    }
}

/**
 * Навигация, устойчивая к двойному тапу. Переход выполняется только если экран,
 * с которого он инициирован, всё ещё на вершине стека (RESUMED). Второй тап
 * (или тап по пункту меню, пока анимация закрытия ещё идёт) приходит, когда
 * [from] уже STARTED — и отбрасывается.
 *
 * Без этого два быстрых тапа клали в бэкстек два экземпляра одного экрана;
 * на части устройств композиция при этом схлопывалась в пустой фон.
 */
private fun NavHostController.navigateOnce(
    from: androidx.navigation.NavBackStackEntry,
    route: String,
) {
    if (from.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
        navigate(route)
    }
}

/**
 * Возврат назад, устойчивый к двойному тапу — зеркало [navigateOnce].
 *
 * Голый popBackStack() при быстром переключении (профиль → назад → профиль,
 * либо два тапа по стрелке подряд) успевал сработать дважды: второй вызов
 * приходил, когда экран уже не RESUMED, и снимал со стека ЛИШНИЙ экран —
 * вплоть до опустошения графа. NavHost оставался без текущего destination и
 * рисовал только фон приложения (пустой экран с сеткой и точками).
 */
private fun NavHostController.popOnce(from: androidx.navigation.NavBackStackEntry) {
    if (from.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
        popBackStack()
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

