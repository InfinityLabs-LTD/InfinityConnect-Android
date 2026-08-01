package com.infinityconnect.vpn.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.domain.model.AppError
import com.infinityconnect.vpn.domain.model.AppResult
import com.infinityconnect.vpn.domain.model.ClientUpdate
import com.infinityconnect.vpn.domain.usecase.CheckClientUpdateUseCase
import com.infinityconnect.vpn.domain.usecase.DownloadClientUpdateUseCase
import com.infinityconnect.vpn.ui.util.ErrorMessage
import com.infinityconnect.vpn.util.ApkInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Состояние блока «Обновление» на экране «О приложении». */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val update: ClientUpdate) : UpdateUiState
    data class Downloading(val update: ClientUpdate, val progress: Float) : UpdateUiState
    data class Error(val message: ErrorMessage) : UpdateUiState
}

/** VM экрана «О приложении»: проверка/скачивание/установка обновления клиента. */
@HiltViewModel
class AboutViewModel @Inject constructor(
    private val checkUpdate: CheckClientUpdateUseCase,
    private val downloadUpdate: DownloadClientUpdateUseCase,
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    /** Ручная проверка обновления (кнопка на экране). */
    fun check() {
        if (_updateState.value is UpdateUiState.Checking ||
            _updateState.value is UpdateUiState.Downloading
        ) return
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            when (val result = checkUpdate()) {
                is AppResult.Success -> _updateState.value =
                    result.data?.let { UpdateUiState.Available(it) } ?: UpdateUiState.UpToDate
                is AppResult.Failure -> _updateState.value =
                    UpdateUiState.Error(result.error.toUpdateMessage())
            }
        }
    }

    /** Скачивает APK и открывает системный установщик. */
    fun downloadAndInstall(context: Context, update: ClientUpdate) {
        if (_updateState.value is UpdateUiState.Downloading) return
        _updateState.value = UpdateUiState.Downloading(update, 0f)
        val appContext = context.applicationContext
        viewModelScope.launch {
            val result = downloadUpdate(update) { p ->
                _updateState.value = UpdateUiState.Downloading(update, p)
            }
            when (result) {
                is AppResult.Success -> {
                    _updateState.value = UpdateUiState.Available(update)
                    ApkInstaller.install(appContext, result.data)
                }
                is AppResult.Failure -> _updateState.value =
                    UpdateUiState.Error(result.error.toUpdateMessage())
            }
        }
    }
}

private fun AppError.toUpdateMessage(): ErrorMessage = when (this) {
    is AppError.Network -> ErrorMessage(R.string.update_error_network)
    // Текст от сервера конкретнее нашей формулировки — показываем его, если есть.
    is AppError.Parse -> ErrorMessage(R.string.update_error_corrupted, raw = message)
    is AppError.Server -> ErrorMessage(R.string.update_error_server, listOf(code))
    else -> ErrorMessage(R.string.update_error_unknown)
}
