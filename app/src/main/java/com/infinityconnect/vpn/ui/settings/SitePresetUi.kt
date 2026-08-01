package com.infinityconnect.vpn.ui.settings

import androidx.annotation.StringRes
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.domain.model.SitePreset

/**
 * Названия и описания пресетов доменной маршрутизации для UI.
 *
 * Живут здесь, а не в [SitePreset]: enum — доменная модель, она не должна
 * держать локализуемые строки (их нельзя достать из ресурсов без Context, и
 * при смене языка значения enum не перечитываются).
 */
@StringRes
fun SitePreset.titleRes(): Int = when (this) {
    SitePreset.RU_BYPASS -> R.string.preset_ru_direct
    SitePreset.RU_BANKS -> R.string.preset_banks_direct
    SitePreset.RU_SERVICES -> R.string.preset_ru_services_direct
    SitePreset.STREAMING_PROXY -> R.string.preset_streaming_proxy
    SitePreset.SOCIAL_PROXY -> R.string.preset_social_proxy
}

@StringRes
fun SitePreset.subtitleRes(): Int = when (this) {
    SitePreset.RU_BYPASS -> R.string.preset_ru_direct_desc
    SitePreset.RU_BANKS -> R.string.preset_banks_direct_desc
    SitePreset.RU_SERVICES -> R.string.preset_ru_services_direct_desc
    SitePreset.STREAMING_PROXY -> R.string.preset_streaming_proxy_desc
    SitePreset.SOCIAL_PROXY -> R.string.preset_social_proxy_desc
}
