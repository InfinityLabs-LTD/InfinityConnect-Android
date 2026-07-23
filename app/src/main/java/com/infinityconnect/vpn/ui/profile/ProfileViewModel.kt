package com.infinityconnect.vpn.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.SubscriptionInfo
import com.infinityconnect.vpn.domain.model.UserInfo
import com.infinityconnect.vpn.domain.model.VpnKey
import com.infinityconnect.vpn.domain.repository.DiscoveryRepository
import com.infinityconnect.vpn.domain.repository.KeysRepository
import com.infinityconnect.vpn.domain.repository.UserRepository
import com.infinityconnect.vpn.domain.usecase.LogoutUseCase
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
    /** Тариф из активных ключей: «Базовый», «Премиум» или «Базовый + Премиум». */
    val planLabel: String? = null,
    /** Ключи пользователя — для перечня сроков по каждой подписке. */
    val keys: List<VpnKey> = emptyList(),
    val supportUrl: String? = null,
    val error: String? = null,
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
                    planLabel = planLabel(keys, user),
                    keys = keys,
                    error = if (userResult is AppResult.Failure) "Не удалось загрузить профиль" else null,
                )
            }
        }
    }

    /**
     * Тариф по составу ключей: обычные ключи → «Базовый» (все сервера),
     * премиум-ключи → «Премиум»; есть и те и другие → «Базовый + Премиум».
     * Ключей в кэше нет — fallback на plan_name от сервера.
     */
    private fun planLabel(keys: List<VpnKey>, user: UserInfo?): String? {
        val active = keys.filter { it.isActive }
        val hasBase = active.any { !it.isPremium }
        val hasPremium = active.any { it.isPremium }
        return when {
            hasBase && hasPremium -> "Базовый + Премиум"
            hasPremium -> "Премиум"
            hasBase -> "Базовый"
            else -> user?.planName
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
