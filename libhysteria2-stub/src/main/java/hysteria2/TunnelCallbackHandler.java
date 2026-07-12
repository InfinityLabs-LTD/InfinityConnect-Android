package hysteria2;

/**
 * СТАБ (compileOnly) обработчика событий клиента Hysteria2 (сгенерирован
 * gomobile). Реализацию передаёт вызывающий код.
 */
public interface TunnelCallbackHandler {

    /** Клиент завершил работу (разрыв соединения / остановка изнутри). */
    void onShutdown();

    /** Диагностическое сообщение от клиента (уровень + текст). */
    void onStatus(long level, String message);
}
