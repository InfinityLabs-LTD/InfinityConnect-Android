package com.infinityconnect.vpn.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единый источник правды о состоянии авторизации. На него подписывается
 * навигация; его обновляют и AuthRepository (login/logout), и token-provider
 * (авто-логаут при истёкшем refresh из фонового OkHttp Authenticator).
 *
 * Вынесен отдельно, чтобы разорвать цикл: token-provider не должен зависеть от
 * AuthRepository, но обязан уметь сообщить о разлогине.
 */
@Singleton
class SessionState @Inject constructor(
    tokenStorage: TokenStorage,
) {
    private val _loggedIn = MutableStateFlow(tokenStorage.hasSession())
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    fun setLoggedIn(value: Boolean) {
        _loggedIn.value = value
    }
}
