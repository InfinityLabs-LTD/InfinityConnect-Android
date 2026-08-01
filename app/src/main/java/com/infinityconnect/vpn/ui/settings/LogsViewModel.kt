package com.infinityconnect.vpn.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.data.local.LogEntry
import com.infinityconnect.vpn.data.local.LogLevel
import com.infinityconnect.vpn.data.local.LogStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Фильтр журнала по минимальному уровню. */
enum class LogFilter(@StringRes val labelRes: Int, val minLevel: LogLevel) {
    ALL(R.string.logs_filter_all, LogLevel.DEBUG),
    INFO(R.string.logs_filter_info, LogLevel.INFO),
    WARN(R.string.logs_filter_warn, LogLevel.WARN),
    ERROR(R.string.logs_filter_error, LogLevel.ERROR),
}

/** Состояние экрана логов. */
data class LogsUiState(
    val filter: LogFilter = LogFilter.ALL,
    /** Автопрокрутка к свежим записям — отключается, если пользователь листает. */
    val autoScroll: Boolean = true,
    /** Файл, подготовленный к отправке (кнопка «Поделиться»). */
    val exportFile: File? = null,
    /**
     * Сообщение для снекбара — ID строки, а не текст: VM не должна собирать
     * локализованные строки (у неё нет Context, а язык может смениться между
     * записью и показом). Резолвится на экране через stringResource.
     */
    @StringRes val notice: Int? = null,
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val logStore: LogStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(LogsUiState())
    val ui: StateFlow<LogsUiState> = _ui.asStateFlow()

    /**
     * Записи с учётом фильтра. Пересобирается и при новых строках, и при смене
     * фильтра, поэтому список в UI всегда согласован с выбранным уровнем.
     */
    val entries: StateFlow<List<LogEntry>> =
        combine(logStore.entries, _ui) { all, state ->
            all.filter { it.level.ordinal >= state.filter.minLevel.ordinal }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: LogFilter) = _ui.update { it.copy(filter = filter) }

    fun setAutoScroll(enabled: Boolean) = _ui.update { it.copy(autoScroll = enabled) }

    /** Готовит файл журнала и отдаёт его UI для системного «Поделиться». */
    fun share() {
        viewModelScope.launch {
            val file = logStore.exportToCache()
            _ui.update {
                if (file == null) it.copy(notice = R.string.logs_export_failed)
                else it.copy(exportFile = file)
            }
        }
    }

    /** Сбрасывает файл после запуска системного диалога отправки. */
    fun shareHandled() = _ui.update { it.copy(exportFile = null) }

    fun noticeShown() = _ui.update { it.copy(notice = null) }

    fun clear() {
        viewModelScope.launch {
            logStore.clear()
            _ui.update { it.copy(notice = R.string.logs_cleared) }
        }
    }
}
