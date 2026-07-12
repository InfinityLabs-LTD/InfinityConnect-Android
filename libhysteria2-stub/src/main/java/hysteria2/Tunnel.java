package hysteria2;

/**
 * СТАБ (compileOnly) запущенного туннеля Hysteria2 (сгенерирован gomobile).
 *
 * Сигнатуры соответствуют реальному AAR. Реальная реализация — в нативном AAR.
 */
public final class Tunnel {

    /** Конструктор соответствует gomobile-обёртке (эквивалент newTunnel). */
    public Tunnel(String configJson, long tunFd, long mtu, String tunCidr, TunnelCallbackHandler handler) {
        // stub
    }

    /** Останавливает клиент и закрывает соединение. Идемпотентно. */
    public void close() throws Exception {
        throw new UnsupportedOperationException("stub");
    }

    /** Суммарно получено байт с момента запуска. */
    public long downlinkBytes() {
        return 0;
    }

    /** Суммарно отправлено байт с момента запуска. */
    public long uplinkBytes() {
        return 0;
    }
}
