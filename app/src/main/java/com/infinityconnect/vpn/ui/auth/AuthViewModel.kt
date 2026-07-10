package com.infinityconnect.vpn.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.repository.DiscoveryRepository
import com.infinityconnect.vpn.domain.usecase.LoginAndSyncUseCase
import com.infinityconnect.vpn.ui.util.toMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val login: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val registerUrl: String? = null,
    val forgotPasswordUrl: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginAndSync: LoginAndSyncUseCase,
    discoveryRepository: DiscoveryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AuthUiState(
            registerUrl = discoveryRepository.cached()?.registerUrl,
            forgotPasswordUrl = discoveryRepository.cached()?.forgotPasswordUrl,
        ),
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onLoginChange(value: String) = _state.update { it.copy(login = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    /** onSuccess вызывается при успешном входе (навигация на главную). */
    fun submit(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.login.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "Введите логин и пароль") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = loginAndSync(s.login.trim(), s.password)) {
                is AppResult.Success -> {
                    _state.update { it.copy(loading = false) }
                    onSuccess()
                }
                is AppResult.Failure ->
                    _state.update { it.copy(loading = false, error = result.error.toMessage()) }
            }
        }
    }
}
