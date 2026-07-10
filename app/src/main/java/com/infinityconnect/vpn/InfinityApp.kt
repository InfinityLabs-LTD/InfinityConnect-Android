package com.infinityconnect.vpn

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Точка входа приложения. Инициализирует Hilt-граф зависимостей.
 */
@HiltAndroidApp
class InfinityApp : Application()
