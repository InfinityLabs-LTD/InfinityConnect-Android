package com.infinityconnect.vpn.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
enum class LogFilter(val label: String, val minLevel: LogLevel) {
    ALL("Все", LogLevel.DEBUG),
    INFO("Инфо", LogLevel.INFO),
    WARN("Предупреждения", LogLevel.WARN),
    ERROR("Ошибки", LogLevel.ERROR),
}

/** Состояние экрана логов. */
data class LogsUiState(
    val filter: LogFilter = LogFilter.ALL,
    /** Автопрокрутка к свежим записям — отключается, если пользователь листает. */
    val autoScroll: Boolean = true,
    /** Файл, подготовленный к отправке (кнопка «Поделиться»). */
    val exportFile: File? = null,
    val notice: String? = null,
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
                if (file == null) it.copy(notice = "Не удалось подготовить файл журнала")
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
            _ui.update { it.copy(notice = "Журнал очищен") }
        }
    }
}
