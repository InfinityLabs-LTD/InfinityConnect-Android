package com.infinityconnect.vpn.data.repository

import com.infinityconnect.vpn.data.local.SessionState
import com.infinityconnect.vpn.data.local.TokenStorage
import com.infinityconnect.vpn.data.remote.api.InfinityApi
import com.infinityconnect.vpn.data.remote.dto.LoginRequestDto
import com.infinityconnect.vpn.domain.model.AppError
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Авторизация по веб-аккаунту. Управляет токенами в [TokenStorage] и общим
 * [SessionState], на который реагирует навигация (в т.ч. авто-логаут при
 * истёкшем refresh, инициированный из сетевого слоя).
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: InfinityApi,
    private val storage: TokenStorage,
    private val sessionState: SessionState,
) : AuthRepository {

    override val isLoggedIn: Flow<Boolean> = sessionState.loggedIn

    override suspend fun login(login: String, password: String): AppResult<Unit> {
        val result = safeApiCall { api.login(LoginRequestDto(login, password)) }
        return when (result) {
            is AppResult.Success -> {
                val tokens = result.data
                storage.save(tokens.accessToken, tokens.refreshToken)
                sessionState.setLoggedIn(true)
                AppResult.Success(Unit)
            }
            is AppResult.Failure -> {
                // 401 на логине — неверные учётные данные, а не истёкшая сессия.
                if (result.error is AppError.Unauthorized) {
                    AppResult.Failure(AppError.InvalidCredentials)
                } else {
                    result
                }
            }
        }
    }

    override suspend fun logout() {
        // Серверный logout — best-effort: даже при ошибке чистим локальные токены.
        runCatching { api.logout() }
        storage.clear()
        sessionState.setLoggedIn(false)
    }
}
