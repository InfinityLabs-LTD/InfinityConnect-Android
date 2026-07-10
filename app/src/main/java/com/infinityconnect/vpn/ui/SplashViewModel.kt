package com.infinityconnect.vpn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.repository.AuthRepository
import com.infinityconnect.vpn.domain.repository.DiscoveryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Начальный маршрут после инициализации. */
enum class StartDestination { ONBOARDING, AUTH, HOME }

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

    private fun resolve() {
        viewModelScope.launch {
            // 1. Нет сохранённого discovery → онбординг.
            when (discoveryRepository.restore()) {
                is AppResult.Failure -> {
                    _destination.value = StartDestination.ONBOARDING
                    return@launch
                }
                is AppResult.Success -> Unit
            }
            // 2. Есть discovery → смотрим на сессию.
            val loggedIn = authRepository.isLoggedIn.first()
            _destination.value = if (loggedIn) StartDestination.HOME else StartDestination.AUTH
        }
    }
}
