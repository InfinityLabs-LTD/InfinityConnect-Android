package com.infinityconnect.vpn.ui.settings

import androidx.compose.ui.graphics.Color
import com.infinityconnect.vpn.domain.model.PingMethod
import com.infinityconnect.vpn.ui.theme.InfinityColors

/** Отображаемое название метода пинга. */
fun PingMethod.title(): String = when (this) {
    PingMethod.REAL -> "Реальный (через протокол)"
    PingMethod.PROXY_GET -> "Через прокси (GET)"
    PingMethod.PROXY_HEAD -> "Через прокси (HEAD)"
    PingMethod.TCP -> "TCP"
    PingMethod.ICMP -> "ICMP"
}

/** Пояснение метода пинга для настроек. */
fun PingMethod.description(): String = when (this) {
    PingMethod.REAL ->
        "Задержка через сам протокол сервера (ядро Xray): HTTP-запрос к тест-URL через VLESS/Reality. Самый точный метод. Для Hysteria2 — откат на TCP."
    PingMethod.PROXY_GET ->
        "HTTP GET до тест-URL: полный ответ. Ближе всего к реальной задержке сайта, но значения выше."
    PingMethod.PROXY_HEAD ->
        "HTTP HEAD до тест-URL: только заголовки, без тела. Легче GET, значения чуть ниже."
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
    PingMethod.REAL -> InfinityColors.PingReal
    PingMethod.TCP -> InfinityColors.PingTcp
    PingMethod.ICMP -> InfinityColors.PingIcmp
    PingMethod.PROXY_GET -> InfinityColors.PingGet
    PingMethod.PROXY_HEAD -> InfinityColors.PingHead
}

/** Порог «хорошо/средне» в мс — зависит от метода (HTTP медленнее TCP/ICMP). */
private fun PingMethod.thresholds(): Pair<Int, Int> = when (this) {
    // REAL идёт через протокол (TLS+HTTP), значения выше чистого TCP/ICMP.
    PingMethod.REAL -> 200 to 500
    PingMethod.TCP, PingMethod.ICMP -> 100 to 250
    PingMethod.PROXY_GET, PingMethod.PROXY_HEAD -> 300 to 700
}

/**
 * Итоговый цвет пинг-значения: базовый оттенок метода, приглушённый по качеству
 * (хорошо → яркий базовый, средне → янтарный, плохо → коралловый).
 */
fun pingColor(method: PingMethod, pingMs: Int?): Color {
    if (pingMs == null) return InfinityColors.Muted
    if (pingMs < 0) return InfinityColors.MutedDim
    val (good, mid) = method.thresholds()
    return when {
        pingMs < good -> method.baseColor()
        pingMs < mid -> InfinityColors.Amber
        else -> InfinityColors.Coral
    }
}
