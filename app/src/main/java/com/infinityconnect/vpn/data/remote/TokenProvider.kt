package com.infinityconnect.vpn.data.remote

/**
 * Абстракция доступа к токенам для сетевого слоя (интерцептор/аутентификатор).
 *
 * Позволяет OkHttp-компонентам читать/обновлять токены, не завися от
 * конкретной реализации хранилища (Keystore) — та появится на этапе storage.
 * Методы синхронные: OkHttp Interceptor/Authenticator работают в блокирующем
 * стиле, поэтому реализация должна читать/писать токены без корутин
 * (например, через runBlocking над DataStore/EncryptedSharedPreferences).
 */
interface TokenProvider {

    /** Текущий access-токен или null, если пользователь не авторизован. */
    fun accessToken(): String?

    /** Текущий refresh-токен или null. */
    fun refreshToken(): String?

    /**
     * Синхронно обновляет пару токенов через refresh-эндпоинт.
     * @return новый access-токен при успехе, либо null (refresh истёк/ошибка).
     * null сам по себе НЕ повод для разлогина — сначала спросите
     * [lastRefreshRejected]: сетевой сбой не должен выкидывать пользователя.
     */
    fun refreshTokensBlocking(): String?

    /**
     * Результат последней попытки refresh: отверг ли сервер сам токен.
     * true — только при явном отказе (401/403), т.е. refresh больше не валиден.
     * false — сетевая ошибка или 5xx: токен, скорее всего, ещё жив.
     */
    fun lastRefreshRejected(): Boolean

    /** Полный сброс токенов (разлогин). */
    fun clear()
}
