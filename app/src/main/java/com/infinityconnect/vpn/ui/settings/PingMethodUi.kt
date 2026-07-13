package com.infinityconnect.vpn.ui.settings

import androidx.compose.ui.graphics.Color
import com.infinityconnect.vpn.domain.model.PingMethod
import com.infinityconnect.vpn.ui.theme.InfinityColors

/** Отображаемое название протокола пинга. */
fun PingMethod.title(): String = when (this) {
    PingMethod.PROXY_GET -> "Через прокси (GET)"
    PingMethod.PROXY_HEAD -> "Через прокси (HEAD)"
    PingMethod.TCP -> "TCP"
    PingMethod.ICMP -> "ICMP"
}

/** Пояснение протокола пинга для настроек. */
fun PingMethod.description(): String = when (this) {
    PingMethod.PROXY_GET ->
        "HTTP GET к тест-URL через сам протокол сервера (ядро Xray): полный ответ. Самый точный, но значения выше. Для Hysteria2 — откат на TCP."
    PingMethod.PROXY_HEAD ->
        "HTTP HEAD к тест-URL через протокол сервера: только заголовки, без тела. Легче GET, значения чуть ниже. Для Hysteria2 — откат на TCP."
    PingMethod.TCP ->
        "Время TCP-подключения к серверу (host:port). Чистая задержка до узла, без HTTP/TLS."
    PingMethod.ICMP ->
        "ICMP echo (ping) до адреса сервера. Сетевой RTT; часть сетей блокирует ICMP."
}

/**
 * Базовый цвет пинг-пилла для метода. Цвет пинга зависит от метода измерения
 * (у HTTP-методов значения выше, чем у TCP/ICMP), поэтому и цветовая семантика
 * своя для каждого.
 */
fun PingMethod.baseColor(): Color = when (this) {
    PingMethod.TCP -> InfinityColors.PingTcp
    PingMethod.ICMP -> InfinityColors.PingIcmp
    PingMethod.PROXY_GET -> InfinityColors.PingGet
    PingMethod.PROXY_HEAD -> InfinityColors.PingHead
}

/** Порог «хорошо/средне» в мс — зависит от метода (прокси медленнее TCP/ICMP). */
private fun PingMethod.thresholds(): Pair<Int, Int> = when (this) {
    // Прокси-методы идут через протокол (TLS+HTTP), значения выше чистого TCP/ICMP.
    PingMethod.PROXY_GET, PingMethod.PROXY_HEAD -> 300 to 700
    PingMethod.TCP, PingMethod.ICMP -> 100 to 250
}

/**
 * Итоговый цвет пинг-значения по КАЧЕСТВУ связи: хорошо → зелёный (мятный),
 * средне → янтарный, плохо → коралловый. Пороги зависят от метода измерения
 * (HTTP-методы медленнее чистого TCP/ICMP).
 */
fun pingColor(method: PingMethod, pingMs: Int?): Color {
    if (pingMs == null) return InfinityColors.Muted
    if (pingMs < 0) return InfinityColors.MutedDim
    val (good, mid) = method.thresholds()
    return when {
        pingMs < good -> InfinityColors.Mint
        pingMs < mid -> InfinityColors.Amber
        else -> InfinityColors.Coral
    }
}
