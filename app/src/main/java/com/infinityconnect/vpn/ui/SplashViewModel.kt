package com.infinityconnect.vpn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.BuildFlags
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.repository.AuthRepository
import com.infinityconnect.vpn.domain.repository.DiscoveryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Начальный маршрут после инициализации.
 * ONBOARDING нет — домен фиксирован ([BuildFlags.SERVER_DOMAIN]), discovery
 * выполняется автоматически.
 */
enum class StartDestination { AUTH, HOME, ERROR }

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val discoveryRepository: DiscoveryRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _destination = MutableStateFlow<StartDestination?>(null)
    val destination: StateFlow<StartDestination?> = _destination

    init {
        resolve()
    }

    fun retry() = resolve()

    private fun resolve() {
        _destination.value = null
        viewModelScope.launch {
            // 1. Пытаемся восстановить сохранённый discovery; если нет —
            //    выполняем его автоматически по фиксированному домену.
            val ready = when (discoveryRepository.restore()) {
                is AppResult.Success -> true
                is AppResult.Failure ->
                    discoveryRepository.discover(BuildFlags.SERVER_DOMAIN) is AppResult.Success
            }
            if (!ready) {
                // Нет сети / сервер недоступен — покажем экран ошибки с повтором.
                _destination.value = StartDestination.ERROR
                return@launch
            }
            // 2. Discovery готов → смотрим на сессию.
            val loggedIn = authRepository.isLoggedIn.first()
            _destination.value = if (loggedIn) StartDestination.HOME else StartDestination.AUTH
        }
    }
}
