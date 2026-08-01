package com.infinityconnect.vpn.ui.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.SubscriptionInfo
import com.infinityconnect.vpn.domain.model.UserInfo
import com.infinityconnect.vpn.domain.model.VpnKey
import com.infinityconnect.vpn.domain.repository.DiscoveryRepository
import com.infinityconnect.vpn.domain.repository.KeysRepository
import com.infinityconnect.vpn.domain.repository.UserRepository
import com.infinityconnect.vpn.domain.usecase.LogoutUseCase
import com.infinityconnect.vpn.ui.util.ErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val loading: Boolean = true,
    val user: UserInfo? = null,
    val subscription: SubscriptionInfo? = null,
    /**
     * Тариф из активных ключей — ID строки («Базовый», «Премиум», «Базовый +
     * Премиум»). null, если ключей в кэше нет: тогда показывается [planNameRaw].
     */
    @StringRes val planLabelRes: Int? = null,
    /** Название тарифа от сервера — fallback, локализации не подлежит. */
    val planNameRaw: String? = null,
    /** Ключи пользователя — для перечня сроков по каждой подписке. */
    val keys: List<VpnKey> = emptyList(),
    val supportUrl: String? = null,
    /** Ошибка как ID строки + аргументы — резолвится на экране (см. errorText). */
    val error: ErrorMessage? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val keysRepository: KeysRepository,
    private val logoutUseCase: LogoutUseCase,
    discoveryRepository: DiscoveryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProfileUiState(supportUrl = discoveryRepository.cached()?.supportUrl),
    )
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val userResult = userRepository.userInfo()
            val subResult = userRepository.subscriptionInfo()
            val user = (userResult as? AppResult.Success)?.data
            // Кэш ключей мог быть пуст (первый заход в профиль) — подтягиваем.
            if (keysRepository.keys.first().isEmpty()) keysRepository.sync()
            val keys = keysRepository.keys.first()
            _state.update { current ->
                current.copy(
                    loading = false,
                    user = user,
                    subscription = (subResult as? AppResult.Success)?.data,
                    planLabelRes = planLabelRes(keys),
                    planNameRaw = user?.planName,
                    keys = keys,
                    error = if (userResult is AppResult.Failure) {
                        ErrorMessage(R.string.profile_load_failed)
                    } else {
                        null
                    },
                )
            }
        }
    }

    /**
     * Тариф по составу ключей: обычные ключи → «Базовый» (все сервера),
     * премиум-ключи → «Премиум»; есть и те и другие → «Базовый + Премиум».
     * Ключей в кэше нет — null, и экран показывает plan_name от сервера.
     */
    @StringRes
    private fun planLabelRes(keys: List<VpnKey>): Int? {
        val active = keys.filter { it.isActive }
        val hasBase = active.any { !it.isPremium }
        val hasPremium = active.any { it.isPremium }
        return when {
            hasBase && hasPremium -> R.string.profile_plan_base_premium
            hasPremium -> R.string.profile_plan_premium
            hasBase -> R.string.profile_plan_base
            else -> null
        }
    }

    /** Разлогин; onDone вызывается для навигации на экран входа. */
    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            logoutUseCase()
            onDone()
        }
    }
}
