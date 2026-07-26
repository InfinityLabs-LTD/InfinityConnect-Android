package com.infinityconnect.vpn.vpn.xray

import android.content.Context
import android.provider.Settings
import android.util.Base64
import go.Seq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.io.RandomAccessFile

/**
 * Мост к нативному ядру Xray (AndroidLibXrayLite / пакет libv2ray, собран
 * gomobile). Прямые вызовы Go-обёртки; компиляция — против стаб-модуля
 * libv2ray-stub, в рантайме работает реальный AAR.
 *
 * Ключевое отличие актуального API: TUN fd передаётся прямо в ядро через
 * [CoreController.startLoop] — встроенный tun2socks заворачивает трафик TUN в
 * аутбаунды ядра, отдельная tun2socks-библиотека НЕ нужна. Обход собственного
 * трафика ядра мимо TUN (protect) обеспечивается тем, что [Seq.setContext]
 * получает контекст VpnService.
 */
class XrayCoreBridge(
    private val logStore: com.infinityconnect.vpn.data.local.LogStore,
    private val onCoreShutdown: () -> Unit,
) {
    private var controller: CoreController? = null

    @Volatile
    private var envInitialized = false

    /**
     * Файл error-лога ядра и позиция, до которой он уже перелит в журнал.
     * Ядро пишет туда причины отказов соединений — в [CoreCallbackHandler]
     * они не приходят (там только старт/стоп), поэтому дочитываем файл сами.
     */
    private var errorLogFile: File? = null
    private var errorLogOffset = 0L
    private var drainJob: Job? = null
    private val drainScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Инициализирует окружение ядра (единожды на процесс): контекст для доступа
     * к VpnService.protect и путь к geoip/geosite. [serviceContext] должен быть
     * контекстом самого VpnService.
     */
    fun initEnv(serviceContext: Context, assetDir: File) {
        if (envInitialized) return
        // Контекст VpnService — через него ядро вызывает protect() для своих
        // сокетов (иначе исходящий трафик ядра зациклится в TUN).
        Seq.setContext(serviceContext.applicationContext)
        Libv2ray.initCoreEnv(assetDir.absolutePath, xudpBaseKey(serviceContext))
        envInitialized = true
        logStore.i(TAG, "Xray env инициализирован, версия ядра: ${runCatching { Libv2ray.checkVersionX() }.getOrDefault("?")}")
    }

    /**
     * Запускает ядро с JSON-конфигом и TUN-дескриптором.
     *
     * [tunFd] передаётся ядру ВО ВЛАДЕНИЕ: libv2ray закрывает его сам при
     * stopLoop(). Поэтому сюда обязан приходить дубликат дескриптора
     * (`ParcelFileDescriptor.dup().detachFd()`), а не fd, которым ещё владеет
     * VpnService, — двойное закрытие ловит fdsan и роняет процесс.
     *
     * @throws Exception при ошибке запуска (перехватывается движком → Error).
     */
    fun start(configJson: String, tunFd: Int, errorLog: File? = null) {
        // Стартуем с чистого файла: иначе в журнал уехал бы лог прошлой сессии.
        errorLog?.let { file ->
            runCatching { if (file.exists()) file.delete() }
            errorLogFile = file
            errorLogOffset = 0
        }
        val handler = object : CoreCallbackHandler {
            override fun startup(): Long = 0
            override fun shutdown(): Long {
                // Ядро сообщило о завершении — уведомляем сервис.
                runCatching { onCoreShutdown() }
                return 0
            }
            override fun onEmitStatus(l: Long, s: String?): Long {
                // Только события жизненного цикла ядра («Started successfully»,
                // «Core stopped»). Причины отказов соединений сюда НЕ приходят —
                // за ними см. drainErrorLog(): ядро пишет их в свой error-лог.
                if (!s.isNullOrBlank()) logStore.d(TAG, "core[$l]: $s")
                return 0
            }
        }
        val ctrl = Libv2ray.newCoreController(handler)
        ctrl.startLoop(configJson, tunFd)
        controller = ctrl
        startErrorLogDrain()
        logStore.i(TAG, "Xray-ядро запущено (tunFd=$tunFd)")
    }

    /**
     * Периодически переливает свежие строки error-лога ядра в журнал.
     * Читаем с сохранённого смещения, чтобы не дублировать уже перелитое.
     */
    private fun startErrorLogDrain() {
        val file = errorLogFile ?: return
        drainJob?.cancel()
        drainJob = drainScope.launch {
            while (isActive) {
                delay(DRAIN_INTERVAL_MS)
                drainErrorLog(file)
            }
        }
    }

    /** Дочитывает файл с [errorLogOffset] и пишет новые строки в журнал. */
    private fun drainErrorLog(file: File) {
        runCatching {
            if (!file.exists()) return
            val length = file.length()
            // Ядро могло пересоздать файл — тогда читаем с начала.
            if (length < errorLogOffset) errorLogOffset = 0
            if (length == errorLogOffset) return
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(errorLogOffset)
                while (true) {
                    val line = raf.readLine() ?: break
                    // readLine() отдаёт байты как ISO-8859-1 — возвращаем UTF-8.
                    val text = String(line.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8).trim()
                    if (text.isNotEmpty()) logStore.d(TAG, "xray: $text")
                }
                errorLogOffset = raf.filePointer
            }
        }.onFailure { logStore.w(TAG, "Не удалось прочитать error-лог ядра: ${it.message}") }
    }

    /** Останавливает ядро. Идемпотентно. */
    fun stop() {
        val ctrl = controller ?: return
        runCatching { ctrl.stopLoop() }
            .onFailure { logStore.w(TAG, "Ошибка остановки ядра: ${it.message}") }
        controller = null
        // Забираем хвост лога до отмены откачки — там причина остановки.
        drainJob?.cancel()
        drainJob = null
        errorLogFile?.let { drainErrorLog(it) }
        logStore.i(TAG, "Xray-ядро остановлено")
    }

    /**
     * Суммарный трафик аутбаунда "proxy" (uplink+downlink) в байтах, либо -1.
     * Формат queryAllOutboundTrafficStats: "tag,direction,value;...".
     */
    fun queryProxyTraffic(): Pair<Long, Long>? {
        val ctrl = controller ?: return null
        val raw = runCatching { ctrl.queryAllOutboundTrafficStats() }.getOrNull() ?: return null
        if (raw.isBlank()) return null
        var up = 0L
        var down = 0L
        for (entry in raw.split(';')) {
            val parts = entry.split(',')
            if (parts.size != 3) continue
            val direction = parts[1]
            val value = parts[2].toLongOrNull() ?: continue
            when (direction) {
                "uplink" -> up += value
                "downlink" -> down += value
            }
        }
        return up to down
    }

    /**
     * XUDP base key для ядра: ровно 32 байта в base64 (NO_PADDING|URL_SAFE).
     * Ядро паникует, если ключ не 32 байта — берём ANDROID_ID, дополняем/
     * обрезаем до 32 байт (copyOf), кодируем. Алгоритм как в v2rayNG.
     */
    private fun xudpBaseKey(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty().ifBlank { "infinity-connect" }
        val bytes = androidId.toByteArray(Charsets.UTF_8).copyOf(32) // ровно 32 байта
        return Base64.encodeToString(bytes, Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP)
    }

    private companion object {
        const val TAG = "XrayCoreBridge"

        /**
         * Период откачки error-лога ядра. Секунда: строки нужны «почти сразу»
         * (пользователь жалуется в моменте), но чаще читать файл смысла нет.
         */
        const val DRAIN_INTERVAL_MS = 1000L
    }
}
