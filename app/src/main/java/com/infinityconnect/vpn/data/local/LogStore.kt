package com.infinityconnect.vpn.data.local

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Уровень записи лога. */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * Одна строка журнала.
 *
 * @param timestamp время события (мс)
 * @param level уровень
 * @param tag источник — «Xray», «Hysteria2», «VpnService», «Network»…
 * @param message текст
 */
data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
) {
    /** Строка для файла/экспорта: `2026-07-25 15:24:01.123 I/Xray: текст`. */
    fun format(): String =
        "${TIME_FORMAT.get()!!.format(Date(timestamp))} ${level.short()}/$tag: $message"

    private fun LogLevel.short(): String = when (this) {
        LogLevel.DEBUG -> "D"
        LogLevel.INFO -> "I"
        LogLevel.WARN -> "W"
        LogLevel.ERROR -> "E"
    }

    companion object {
        /**
         * SimpleDateFormat не потокобезопасен, а пишут из разных потоков
         * (движки, сервис, UI) — держим по экземпляру на поток.
         */
        internal val TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() =
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        }

        /** Разбирает строку файла обратно в запись; null, если формат чужой. */
        fun parse(line: String): LogEntry? {
            // Формат: "<дата 23 симв.> <L>/<tag>: <message>"
            if (line.length < 26) return null
            val time = runCatching {
                TIME_FORMAT.get()!!.parse(line.substring(0, 23))?.time
            }.getOrNull() ?: return null
            val rest = line.substring(24)
            val slash = rest.indexOf('/')
            val colon = rest.indexOf(':')
            if (slash != 1 || colon < slash) return null
            val level = when (rest[0]) {
                'D' -> LogLevel.DEBUG
                'I' -> LogLevel.INFO
                'W' -> LogLevel.WARN
                'E' -> LogLevel.ERROR
                else -> return null
            }
            return LogEntry(
                timestamp = time,
                level = level,
                tag = rest.substring(slash + 1, colon),
                message = rest.substring(colon + 2).trimStart(),
            )
        }
    }
}

/**
 * Постоянный журнал приложения и ядер.
 *
 * Зачем свой, а не logcat: logcat вытесняется системным кольцевым буфером за
 * минуты, недоступен пользователю без adb и полностью теряется при краше — а
 * именно краши и разрывы туннеля разбирать и нужно.
 *
 * Устройство хранилища:
 *  - файлы в `filesDir/logs`: активный `current.log` + ротация в `prev.log`;
 *  - запись идёт через [Channel] в одном потоке — вызывающие не блокируются,
 *    порядок строк сохраняется;
 *  - каждая порция дописывается и **сбрасывается на диск** (`flush`), поэтому
 *    журнал переживает краш процесса и убийство системой;
 *  - при превышении [MAX_FILE_BYTES] активный файл переносится в `prev.log`,
 *    старый `prev.log` удаляется. Итог — не более двух файлов, ~2 МБ суммарно:
 *    история не подтирается на ровном месте, но и не растёт бесконечно.
 */
@Singleton
class LogStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File by lazy {
        File(context.filesDir, DIR_NAME).apply { runCatching { mkdirs() } }
    }
    private val currentFile: File get() = File(dir, FILE_CURRENT)
    private val previousFile: File get() = File(dir, FILE_PREVIOUS)

    /**
     * Очередь на запись. UNLIMITED: логирование не должно ни блокировать
     * вызывающий поток, ни терять строки на всплеске активности ядра.
     */
    private val queue = Channel<LogEntry>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** «Хвост» журнала в памяти — источник для экрана логов. */
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    /**
     * Момент создания хранилища. Всё, что записано в файл раньше, относится к
     * прошлым сессиям; всё, что позже, уже лежит в памяти этой сессии. Граница
     * нужна, чтобы восстановление с диска не задваивало свежие строки: они
     * попадают и в [_entries] сразу, и в файл через очередь.
     */
    private val startedAtMs = System.currentTimeMillis()

    init {
        scope.launch { loadFromDisk() }
        scope.launch { writeLoop() }
    }

    /** Записывает строку в журнал (и дублирует в logcat для отладки). */
    fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        // Дублируем в logcat: при подключённом adb удобнее смотреть там.
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
        }
        // trySend не бросает и не блокирует: очередь безразмерная.
        queue.trySend(entry)
        _entries.update { (it + entry).takeLast(MEMORY_LIMIT) }
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String) = log(LogLevel.ERROR, tag, message)

    /** Записывает ошибку вместе со стектрейсом — он нужен для разбора крашей. */
    fun e(tag: String, message: String, error: Throwable) {
        log(LogLevel.ERROR, tag, "$message\n${error.stackTraceToString()}")
    }

    /**
     * Единственный писатель: читает очередь и дописывает строки в файл.
     *
     * Забирает всё, что уже скопилось в очереди, одной пачкой — при всплеске
     * логов ядра это на порядок дешевле, чем открывать файл на каждую строку.
     * После пачки — обязательный flush + [java.io.FileDescriptor.sync]: без
     * него хвост остался бы в буфере ОС и потерялся при краше, то есть ровно
     * тогда, когда журнал и нужен.
     */
    private suspend fun writeLoop() {
        val batch = mutableListOf<LogEntry>()
        for (entry in queue) {
            batch.clear()
            batch += entry
            // Догребаем всё уже готовое, не дожидаясь новых строк.
            while (batch.size < BATCH_LIMIT) {
                batch += queue.tryReceive().getOrNull() ?: break
            }
            runCatching {
                rotateIfNeeded()
                val text = batch.joinToString("") { it.format() + "\n" }
                java.io.FileOutputStream(currentFile, /* append = */ true).use { out ->
                    out.write(text.toByteArray())
                    out.flush()
                    // Досылаем на носитель: гарантия переживания краша процесса.
                    runCatching { out.fd.sync() }
                }
            }.onFailure {
                // Писать некуда (нет места/прав) — молча продолжаем: журнал не
                // должен ломать работу приложения.
                Log.w(TAG, "Не удалось записать журнал: ${it.message}")
            }
        }
    }

    /** Переносит переполненный активный файл в prev.log. */
    private fun rotateIfNeeded() {
        val file = currentFile
        if (!file.exists() || file.length() < MAX_FILE_BYTES) return
        runCatching {
            val prev = previousFile
            if (prev.exists()) prev.delete()
            file.renameTo(prev)
        }.onFailure { Log.w(TAG, "Ротация журнала не удалась: ${it.message}") }
    }

    /**
     * Поднимает хвост журнала с диска при старте процесса: пользователь должен
     * видеть логи прошлой сессии — в них и лежит причина падения.
     */
    private fun loadFromDisk() {
        val restored = runCatching {
            val lines = mutableListOf<String>()
            // prev.log идёт первым — он старее current.log.
            listOf(previousFile, currentFile).forEach { file ->
                if (file.exists()) lines += file.readLines()
            }
            parseLines(lines.takeLast(MEMORY_LIMIT))
                // Только прошлые сессии: строки этого запуска уже в памяти.
                .filter { it.timestamp < startedAtMs }
        }.getOrElse {
            Log.w(TAG, "Не удалось прочитать журнал: ${it.message}")
            emptyList()
        }
        if (restored.isNotEmpty()) {
            // Старые записи — перед текущими, порядок хронологический.
            _entries.update { (restored + it).takeLast(MEMORY_LIMIT) }
        }
    }

    /**
     * Синхронно дописывает всё, что скопилось в очереди, и сбрасывает на диск.
     *
     * Нужен ровно в одном месте — в обработчике необработанных исключений:
     * писатель работает в своей корутине, и без принудительного слива процесс
     * умрёт раньше, чем строка о краше дойдёт до файла. Блокирует вызывающий
     * поток, поэтому в обычном коде использовать не нужно.
     */
    fun flushBlocking() {
        val pending = buildList {
            while (true) add(queue.tryReceive().getOrNull() ?: break)
        }
        if (pending.isEmpty()) return
        runCatching {
            val text = pending.joinToString("") { it.format() + "\n" }
            java.io.FileOutputStream(currentFile, /* append = */ true).use { out ->
                out.write(text.toByteArray())
                out.flush()
                runCatching { out.fd.sync() }
            }
        }
    }

    /**
     * Собирает записи из строк файла. Сообщение может занимать несколько строк
     * (стектрейс краша), поэтому всё, что не разбирается как новая запись,
     * дописывается к предыдущей — иначе на экране остался бы заголовок
     * «КРАШ …» без самого стектрейса, ради которого журнал и нужен.
     */
    private fun parseLines(lines: List<String>): List<LogEntry> {
        val result = mutableListOf<LogEntry>()
        lines.forEach { line ->
            val parsed = LogEntry.parse(line)
            when {
                parsed != null -> result += parsed
                result.isEmpty() -> Unit // хвост обрезанной записи в начале файла
                else -> {
                    val last = result.removeAt(result.lastIndex)
                    result += last.copy(message = last.message + "\n" + line)
                }
            }
        }
        return result
    }

    /** Полный текст журнала (обе части) — для экспорта и «Поделиться». */
    suspend fun fullText(): String = runCatching {
        buildString {
            listOf(previousFile, currentFile).forEach { file ->
                if (file.exists()) append(file.readText())
            }
        }
    }.getOrElse { "" }

    /**
     * Готовит файл журнала для отправки во внешнее приложение.
     * Кладём в `cacheDir/logs` — этот каталог отдаётся через FileProvider.
     */
    suspend fun exportToCache(): File? = runCatching {
        val outDir = File(context.cacheDir, DIR_NAME).apply { mkdirs() }
        val out = File(outDir, EXPORT_NAME)
        out.writeText(fullText())
        out
    }.getOrElse {
        Log.w(TAG, "Экспорт журнала не удался: ${it.message}")
        null
    }

    /** Очистка журнала — по явному действию пользователя. */
    suspend fun clear() {
        runCatching {
            currentFile.delete()
            previousFile.delete()
        }.onFailure { Log.w(TAG, "Очистка журнала не удалась: ${it.message}") }
        _entries.value = emptyList()
    }

    private companion object {
        const val TAG = "LogStore"
        const val DIR_NAME = "logs"
        const val FILE_CURRENT = "current.log"
        const val FILE_PREVIOUS = "prev.log"
        const val EXPORT_NAME = "infinity-connect.log"

        /** Порог ротации активного файла (1 МБ) — итого до ~2 МБ истории. */
        const val MAX_FILE_BYTES = 1024L * 1024L

        /** Сколько строк держим в памяти для экрана (файл хранит больше). */
        const val MEMORY_LIMIT = 2000

        /** Максимум строк в одной пачке записи — чтобы не держать файл долго. */
        const val BATCH_LIMIT = 200
    }
}
