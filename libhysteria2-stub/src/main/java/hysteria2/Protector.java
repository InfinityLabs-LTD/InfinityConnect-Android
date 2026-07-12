package hysteria2;

/**
 * СТАБ (compileOnly) платформенного протектора сокетов.
 *
 * Реализуется на стороне приложения (через VpnService.protect) и передаётся в
 * {@link Hysteria2#setContext}. gomobile прокидывает вызов из Go в Java, когда
 * клиенту нужно защитить свой UDP-сокет QUIC от заворачивания в TUN.
 */
public interface Protector {

    /** Защитить сокет с дескриптором fd (VpnService.protect). */
    boolean protect(long fd);
}
