package com.infinityconnect.vpn.ui.navigation

/** Маршруты навигации приложения. */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val AUTH = "auth"
    const val HOME = "home"
    const val PROFILE = "profile"

    /** Экран выбора сервера для ключа. */
    const val SERVERS = "servers/{keyId}/{keyName}"
    fun servers(keyId: Long, keyName: String): String = "servers/$keyId/$keyName"
}
