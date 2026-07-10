package libv2ray;

/**
 * СТАБ (compileOnly) контроллера ядра Xray из AndroidLibXrayLite.
 *
 * Реальная реализация — в нативном AAR (libv2ray). Сигнатуры соответствуют
 * методам gomobile-обёртки CoreController.
 */
public class CoreController {

    /**
     * Запускает ядро с JSON-конфигом Xray. TUN fd передаётся ядру напрямую —
     * встроенный tun2socks заворачивает трафик TUN в аутбаунды ядра.
     * fd == 0 отключает TUN-режим.
     */
    public void startLoop(String configContent, long tunFd) throws Exception {
        throw new UnsupportedOperationException("stub");
    }

    /** Останавливает ядро. */
    public void stopLoop() throws Exception {
        throw new UnsupportedOperationException("stub");
    }

    /** Статистика по одному тегу/направлению. */
    public long queryStats(String tag, String direct) {
        return 0;
    }

    /** Все аутбаунд-счётчики строкой "tag,direction,value;...". */
    public String queryAllOutboundTrafficStats() {
        return "";
    }

    /** Измерение задержки. */
    public long measureDelay(String url) throws Exception {
        throw new UnsupportedOperationException("stub");
    }
}
