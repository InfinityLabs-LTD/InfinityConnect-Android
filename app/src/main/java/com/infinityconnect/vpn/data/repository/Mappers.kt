package com.infinityconnect.vpn.data.repository

import com.infinityconnect.vpn.data.remote.dto.DiscoveryDto
import com.infinityconnect.vpn.data.remote.dto.KeyDto
import com.infinityconnect.vpn.data.remote.dto.ServerEntryDto
import com.infinityconnect.vpn.data.remote.dto.SubscriptionInfoDto
import com.infinityconnect.vpn.data.remote.dto.UserInfoDto
import com.infinityconnect.vpn.domain.model.Discovery
import com.infinityconnect.vpn.domain.model.ServerEntry
import com.infinityconnect.vpn.domain.model.SubscriptionInfo
import com.infinityconnect.vpn.domain.model.UserInfo
import com.infinityconnect.vpn.domain.model.VpnKey
import com.infinityconnect.vpn.domain.model.VpnProtocol

/** Мапперы DTO → доменные модели. */

fun DiscoveryDto.toDomain(): Discovery = Discovery(
    apiBaseUrl = apiBaseUrl,
    siteUrl = siteUrl,
    registerUrl = registerUrl,
    forgotPasswordUrl = forgotPasswordUrl,
    supportUrl = supportUrl,
    projectName = projectName,
    apiVersion = apiVersion,
    trialEnabled = features?.trialEnabled ?: false,
    referralsEnabled = features?.referralsEnabled ?: false,
)

fun UserInfoDto.toDomain(): UserInfo = UserInfo(
    userId = userId,
    username = username,
    email = email,
    isSubscriptionActive = isSubscriptionActive,
    subscriptionExpiresAt = subscriptionExpiresAt,
    planName = planName,
)

fun SubscriptionInfoDto.toDomain(): SubscriptionInfo = SubscriptionInfo(
    isActive = isActive,
    keysCount = keysCount,
    latestExpiry = latestExpiry,
    totalSpent = totalSpent,
    totalMonths = totalMonths,
)

fun KeyDto.toDomain(): VpnKey = VpnKey(
    id = id,
    name = name?.takeIf { it.isNotBlank() } ?: (location ?: "Ключ #$id"),
    serverAddress = serverAddress,
    location = location,
    countryFlag = countryFlag,
    isActive = isActive,
    expiresAt = expiresAt,
    usedTrafficBytes = usedTrafficBytes,
    trafficLimitBytes = trafficLimitBytes,
    protocol = VpnProtocol.from(protocol),
    subscriptionUrl = subscriptionUrl,
)

fun ServerEntryDto.toDomain(): ServerEntry = ServerEntry(
    index = index,
    name = name?.takeIf { it.isNotBlank() } ?: (serverAddress ?: "Сервер #$index"),
    flag = flag,
    serverAddress = serverAddress,
)
