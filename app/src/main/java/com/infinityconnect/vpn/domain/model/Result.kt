package com.infinityconnect.vpn.domain.model

/**
 * Обёртка результата операции репозитория/use-case.
 * Явно разделяет успех и типизированную ошибку, чтобы UI показывал понятные
 * сообщения без утечки исключений сети наружу.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

/** Классификация ошибок для человекочитаемых сообщений в UI. */
sealed interface AppError {
    /** Нет сети / таймаут / хост недоступен. */
    data class Network(val cause: Throwable? = null) : AppError

    /** Неверные учётные данные (401 на логине). */
    data object InvalidCredentials : AppError

    /** Сессия истекла — refresh невалиден, требуется повторный вход. */
    data object Unauthorized : AppError

    /** Ошибка сервера (5xx) или неожиданный код. */
    data class Server(val code: Int, val message: String? = null) : AppError

    /** Некорректный ответ/разбор конфига/подписки. */
    data class Parse(val message: String? = null) : AppError

    /** Прочее/неизвестное. */
    data class Unknown(val cause: Throwable? = null) : AppError
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}
