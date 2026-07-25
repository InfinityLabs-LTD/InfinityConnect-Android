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
            forgotPasswordUrl = resolveForgotPasswordUrl(
                discoveryRepository.cached()?.forgotPasswordUrl,
            ),
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

    private companion object {
        /** Актуальная страница восстановления пароля. */
        const val FORGOT_PASSWORD_URL = "https://infinityconnect.ru/account/password/forgot"

        /** Устаревший путь, который всё ещё отдаёт discovery, — ведёт в 404. */
        const val LEGACY_FORGOT_PATH = "/account/forgot-password"

        /**
         * Ссылка «Забыли пароль». Discovery пока отдаёт устаревший
         * [LEGACY_FORGOT_PATH], поэтому его и пустое значение подменяем на
         * актуальный адрес. Как только сервер начнёт присылать правильный URL,
         * он пройдёт как есть — и подмена сама перестанет срабатывать.
         */
        fun resolveForgotPasswordUrl(fromDiscovery: String?): String = when {
            fromDiscovery.isNullOrBlank() -> FORGOT_PASSWORD_URL
            fromDiscovery.trimEnd('/').endsWith(LEGACY_FORGOT_PATH) -> FORGOT_PASSWORD_URL
            else -> fromDiscovery
        }
    }
}
