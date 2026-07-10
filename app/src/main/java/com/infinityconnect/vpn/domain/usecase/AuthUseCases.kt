package com.infinityconnect.vpn.domain.usecase

import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.repository.AuthRepository
import com.infinityconnect.vpn.domain.repository.KeysRepository
import javax.inject.Inject

/**
 * Вход по веб-аккаунту с последующим автоимпортом ключей.
 * После успешного логина сразу синхронизирует /v1/keys — модель приложения не
 * предусматривает ручного добавления подписок.
 */
class LoginAndSyncUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val keysRepository: KeysRepository,
) {
    suspend operator fun invoke(login: String, password: String): AppResult<Unit> {
        return when (val result = authRepository.login(login, password)) {
            is AppResult.Success -> {
                // Автоимпорт ключей — best-effort: ошибка синка не отменяет вход,
                // список подтянется при следующем обновлении/старте.
                keysRepository.sync()
                AppResult.Success(Unit)
            }
            is AppResult.Failure -> result
        }
    }
}

/** Разлогин. */
class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke() = authRepository.logout()
}
