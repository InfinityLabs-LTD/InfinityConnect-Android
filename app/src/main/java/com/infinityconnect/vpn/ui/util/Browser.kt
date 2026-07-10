package com.infinityconnect.vpn.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Открывает ссылку во внешнем браузере. Используется для «Регистрация»,
 * «Забыли пароль» и поддержки (ссылки из discovery).
 */
fun Context.openInBrowser(url: String?) {
    if (url.isNullOrBlank()) {
        Toast.makeText(this, "Ссылка недоступна", Toast.LENGTH_SHORT).show()
        return
    }
    // support_url может прийти как @username — превращаем в t.me-ссылку.
    val normalized = when {
        url.startsWith("@") -> "https://t.me/${url.removePrefix("@")}"
        url.startsWith("http") -> url
        else -> "https://$url"
    }
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}
