package com.infinityconnect.vpn.domain.repository

import com.infinityconnect.vpn.domain.model.AppResult
import kotlinx.serialization.Serializable

/**
 * Сырое тело подписки + метаданные обновления.
 *
 * @param raw тело подписки (base64 / список URI / JSON-массив конфигов Xray).
 * @param fetchedAtMs когда тело было загружено с сервера (epoch ms).
 * @param updateIntervalHours интервал автообновления из заголовка Remnawave
 *   `profile-update-interval` (по умолчанию 12 часов).
 */
@Serializable
data class SubscriptionBody(
    val raw: String,
    val fetchedAtMs: Long,
    val updateIntervalHours: Int,
)

/**
 * Загрузка тела подписки по subscription_url ключа с кэшированием.
 *
 * Тело кэшируется по URL: [fetch] отдаёт кэш, пока он свежий (моложе
 * updateIntervalHours), и ходит в сеть только при устаревании или [forceRefresh].
 * Так серверы предзагружаются один раз и переиспользуются при выборе подписки,
 * а обновляются по кнопке ⟳ (forceRefresh) или раз в интервал из Remnawave.
 */
interface SubscriptionRepository {

    /**
     * @param forceRefresh игнорировать кэш и загрузить заново (кнопка обновления).
     * @return тело + метаданные; при сетевой ошибке, но наличии кэша — вернёт кэш.
     */
    suspend fun fetch(subscriptionUrl: String, forceRefresh: Boolean = false): AppResult<SubscriptionBody>

    /** Есть ли в кэше свежее (не устаревшее) тело для URL — можно не грузить. */
    fun isFresh(subscriptionUrl: String): Boolean
}
