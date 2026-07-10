package com.infinityconnect.vpn.domain.repository

import com.infinityconnect.vpn.domain.model.AppResult

/**
 * Загрузка сырого тела подписки по subscription_url ключа.
 * Тело (base64/список URI) затем разбирается SubscriptionParser на клиенте —
 * это первичный источник конфигов для XHTTP и Hysteria2.
 */
interface SubscriptionRepository {
    suspend fun fetchRawSubscription(subscriptionUrl: String): AppResult<String>
}
