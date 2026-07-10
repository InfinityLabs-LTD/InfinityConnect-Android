package com.infinityconnect.vpn.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.SubscriptionInfo
import com.infinityconnect.vpn.domain.model.UserInfo
import com.infinityconnect.vpn.domain.repository.DiscoveryRepository
import com.infinityconnect.vpn.domain.repository.UserRepository
import com.infinityconnect.vpn.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val loading: Boolean = true,
    val user: UserInfo? = null,
    val subscription: SubscriptionInfo? = null,
    val supportUrl: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
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
            _state.update { current ->
                current.copy(
                    loading = false,
                    user = (userResult as? AppResult.Success)?.data,
                    subscription = (subResult as? AppResult.Success)?.data,
                    error = if (userResult is AppResult.Failure) "Не удалось загрузить профиль" else null,
                )
            }
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
