package com.infinityconnect.vpn.vpn

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.infinityconnect.vpn.R
import com.infinityconnect.vpn.data.local.SettingsStore
import com.infinityconnect.vpn.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Плитка быстрых настроек (шторка Android): показывает состояние туннеля и
 * подключает/отключает VPN одним тапом.
 *
 * Живёт в UI-процессе (в манифесте у неё НЕТ `android:process`), поэтому:
 *  - состояние читается из [VpnStateHolder] UI-процесса, который наполняется
 *    броадкастами [VpnStateBridge] из процесса `:vpn`;
 *  - на холодном старте (шторку открыли, UI-процесс только что подняли) holder
 *    ещё пуст и ни одного броадкаста не приходило — тогда факт работы туннеля
 *    определяем по живому [InfinityVpnService] через ActivityManager
 *    (для СВОЕГО приложения этот API не урезан с Android 8).
 *
 * Команды туннелю идут через [VpnController], как и из UI, — своей логики
 * подключения плитка не заводит.
 */
@AndroidEntryPoint
class VpnTileService : TileService() {

    @Inject lateinit var vpnController: VpnController
    @Inject lateinit var stateHolder: VpnStateHolder
    @Inject lateinit var settingsStore: SettingsStore

    /** Своя область корутин: TileService — обычный Service, без lifecycleScope. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Подписка на состояние туннеля — только пока плитка видна пользователю. */
    private var stateJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        // Плитка видна: следим за состоянием, чтобы подпись менялась вживую
        // (например, пока туннель поднимается, а шторка ещё открыта).
        stateJob?.cancel()
        stateJob = stateHolder.state
            .onEach { render(it) }
            .launchIn(scope)
        render(stateHolder.state.value)
    }

    override fun onStopListening() {
        stateJob?.cancel()
        stateJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val active = isTunnelUp()

        if (active) {
            // Отключение разрешения не требует — просто гасим туннель.
            vpnController.disconnect()
            renderState(Tile.STATE_INACTIVE, getString(R.string.tile_label))
            return
        }

        // (а) Разрешение VpnService не выдано. Из плитки системный диалог
        // показать нельзя (нет Activity-контекста и окно шторки поверх), поэтому
        // открываем приложение — пользователь подтвердит разрешение там.
        if (vpnController.prepareIntent() != null) {
            openApp()
            return
        }

        // (б) Последний сервер неизвестен (первый запуск / после очистки данных) —
        // подключать нечего, открываем приложение, чтобы выбрать сервер.
        scope.launch {
            val last = runCatching { settingsStore.currentLastServer() }.getOrNull()
            if (last == null) {
                openApp()
                return@launch
            }
            renderState(Tile.STATE_ACTIVE, getString(R.string.tile_connecting))
            vpnController.connect(last.keyId, last.serverIndex, last.serverName)
        }
    }

    /**
     * Открывает MainActivity и сворачивает шторку.
     *
     * С Android 14 запуск Activity из плитки допустим только через
     * `startActivityAndCollapse(PendingIntent)`; старая перегрузка с Intent на
     * этих версиях бросает [UnsupportedOperationException].
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    /**
     * Туннель поднят, поднимается или удерживается kill-switch'ем?
     *
     * Error здесь приравнен к «поднят», если сервис ещё жив: при аварийном
     * обрыве с включённым kill-switch [InfinityVpnService] намеренно НЕ
     * закрывает TUN и не завершается — интерфейс продолжает поглощать трафик,
     * чтобы тот не пошёл мимо VPN. Считать такое состояние отключённым нельзя:
     * тап по плитке ушёл бы в ветку подключения, и пользователь не смог бы
     * снять блокировку трафика из шторки.
     */
    private fun isTunnelUp(): Boolean = when (stateHolder.state.value) {
        TunnelState.Connected, TunnelState.Connecting -> true
        // Holder мог не успеть наполниться (см. KDoc класса) — проверяем сервис.
        // Для Error та же проверка отвечает и на вопрос «держит ли kill-switch».
        TunnelState.Disconnected, is TunnelState.Error -> isVpnServiceRunning()
        else -> false
    }

    /** Рисует плитку по состоянию туннеля. */
    private fun render(state: TunnelState) {
        val (tileState, label) = when (state) {
            TunnelState.Connected -> Tile.STATE_ACTIVE to getString(R.string.tile_connected)
            TunnelState.Connecting -> Tile.STATE_ACTIVE to getString(R.string.tile_connecting)
            TunnelState.Disconnecting -> Tile.STATE_UNAVAILABLE to getString(R.string.tile_disconnecting)
            // При аварии с kill-switch сервис жив и держит TUN: трафик
            // заблокирован, а не «просто ошибка». Показываем плитку активной,
            // иначе пользователь не поймёт, почему интернет не работает, и
            // будет искать причину не там (см. isTunnelUp).
            is TunnelState.Error ->
                if (isVpnServiceRunning()) Tile.STATE_ACTIVE to getString(R.string.tile_blocked)
                else Tile.STATE_INACTIVE to getString(R.string.tile_error)
            TunnelState.Disconnected ->
                if (isVpnServiceRunning()) Tile.STATE_ACTIVE to getString(R.string.tile_connected)
                else Tile.STATE_INACTIVE to getString(R.string.tile_label)
        }
        renderState(tileState, label)
    }

    private fun renderState(tileState: Int, label: String) {
        val tile = qsTile ?: return
        tile.state = tileState
        tile.label = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_logo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = label
        }
        tile.contentDescription = getString(R.string.tile_label) + ": " + label
        tile.updateTile()
    }

    /**
     * Работает ли [InfinityVpnService] (процесс `:vpn`). `getRunningServices`
     * с Android 8 не отдаёт чужие сервисы, но СВОИ возвращает — а нам нужен
     * ровно свой, когда broadcast'а состояния ещё не было.
     */
    @Suppress("DEPRECATION")
    private fun isVpnServiceRunning(): Boolean {
        val am = getSystemService(ActivityManager::class.java) ?: return false
        val name = InfinityVpnService::class.java.name
        return runCatching {
            am.getRunningServices(MAX_RUNNING_SERVICES).any { it.service.className == name }
        }.getOrDefault(false)
    }

    private companion object {
        /** Своих сервисов у приложения единицы — берём с запасом. */
        const val MAX_RUNNING_SERVICES = 32
    }
}
