package hysteria2;

/**
 * СТАБ (compileOnly) пакетного фасада Hysteria2, сгенерированного gomobile из
 * обёртки github.com/infinityconnect/hysteria2mobile (клиент apernet/hysteria).
 *
 * Сигнатуры точно соответствуют реальному AAR (libhysteria2.aar). Реальная
 * реализация — в нативном AAR; стаб нужен только для компиляции без AAR.
 *
 * Клиент hysteria2 сам читает/пишет TUN (встроенный gVisor-стек sing-tun) —
 * отдельная tun2socks-библиотека НЕ нужна. Обход собственного трафика клиента
 * мимо TUN обеспечивается через {@link #setContext} с {@link Protector}
 * (VpnService.protect для UDP-сокета QUIC).
 */
public abstract class Hysteria2 {

    /** Триггерит загрузку нативной библиотеки (как в gomobile-выводе). */
    public static void touch() {
        // stub
    }

    /**
     * Запускает клиент hysteria2 с JSON-конфигом и TUN-дескриптором.
     *
     * @param configJson конфиг клиента (server/auth/tls/obfs/bandwidth) в JSON.
     * @param tunFd      файловый дескриптор TUN-интерфейса. Обёртка ДУБЛИРУЕТ его
     *                   (dup) и владеет копией: вызывающая сторона обязана
     *                   закрыть свой ParcelFileDescriptor сама, но только ПОСЛЕ
     *                   {@link Tunnel#close()}. Передавать сюда detach-нутый
     *                   дескриптор не нужно.
     * @param mtu        MTU туннеля.
     * @param tunCidr    адрес TUN с префиксом (например "10.10.0.1/30") — нужен
     *                   системному стеку sing-tun. ПЕРВЫЙ адрес префикса стек
     *                   считает своим и биндит на него TCP-форвардер, поэтому он
     *                   обязан быть выставлен на TUN через
     *                   VpnService.Builder.addAddress.
     * @param handler    обработчик событий клиента.
     * @return запущенный туннель.
     * @throws Exception при ошибке запуска.
     */
    public static Tunnel newTunnel(String configJson, long tunFd, long mtu, String tunCidr, TunnelCallbackHandler handler)
            throws Exception {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Передаёт платформенный {@link Protector} в Go-слой — через него клиент
     * защищает свой UDP-сокет (VpnService.protect), чтобы его трафик не
     * заворачивался обратно в TUN.
     */
    public static void setContext(Protector protector) {
        // stub
    }

    /** Версия обёртки/ядра Hysteria2. */
    public static String version() {
        return "stub";
    }
}
