package com.infinityconnect.vpn.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.repository.DiscoveryRepository
import com.infinityconnect.vpn.ui.util.toMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val domain: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val discoveryRepository: DiscoveryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onDomainChange(value: String) {
        _state.update { it.copy(domain = value, error = null) }
    }

    fun submit() {
        val domain = _state.value.domain.trim()
        if (domain.isBlank()) {
            _state.update { it.copy(error = "Введите домен сервера") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = discoveryRepository.discover(domain)) {
                is AppResult.Success ->
                    _state.update { it.copy(loading = false, success = true) }
                is AppResult.Failure ->
                    _state.update { it.copy(loading = false, error = result.error.toMessage()) }
            }
        }
    }
}
