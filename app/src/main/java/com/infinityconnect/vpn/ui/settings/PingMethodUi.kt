package com.infinityconnect.vpn.ui.settings

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.domain.model.PingMethod
import com.infinityconnect.vpn.ui.theme.InfinityColors

/** Отображаемое название протокола пинга. */
@StringRes
fun PingMethod.titleRes(): Int = when (this) {
    PingMethod.PROXY_GET -> R.string.ping_method_proxy_get
    PingMethod.PROXY_HEAD -> R.string.ping_method_proxy_head
    PingMethod.TCP -> R.string.ping_method_tcp
    PingMethod.ICMP -> R.string.ping_method_icmp
}

/** Пояснение протокола пинга для настроек. */
@StringRes
fun PingMethod.descriptionRes(): Int = when (this) {
    PingMethod.PROXY_GET -> R.string.ping_method_proxy_get_desc
    PingMethod.PROXY_HEAD -> R.string.ping_method_proxy_head_desc
    PingMethod.TCP -> R.string.ping_method_tcp_desc
    PingMethod.ICMP -> R.string.ping_method_icmp_desc
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
