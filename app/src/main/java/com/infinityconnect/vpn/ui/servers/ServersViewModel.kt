package com.infinityconnect.vpn.ui.servers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.ServerEntry
import com.infinityconnect.vpn.domain.usecase.GetServersUseCase
import com.infinityconnect.vpn.ui.util.toMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServersUiState(
    val keyName: String = "",
    val servers: List<ServerEntry> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val getServers: GetServersUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val keyId: Long = savedStateHandle.get<String>("keyId")?.toLongOrNull() ?: -1
    private val keyName: String = savedStateHandle.get<String>("keyName").orEmpty()

    private val _state = MutableStateFlow(ServersUiState(keyName = keyName))
    val state: StateFlow<ServersUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = getServers(keyId)) {
                is AppResult.Success ->
                    _state.update { it.copy(loading = false, servers = result.data) }
                is AppResult.Failure ->
                    _state.update { it.copy(loading = false, error = result.error.toMessage()) }
            }
        }
    }
}
