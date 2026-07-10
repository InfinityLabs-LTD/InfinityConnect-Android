package libv2ray;

/**
 * СТАБ (compileOnly) пакетного фасада AndroidLibXrayLite.
 *
 * gomobile генерирует класс Libv2ray со статическими методами-обёртками
 * над пакетными функциями Go. Реальная реализация — в нативном AAR.
 */
public final class Libv2ray {

    private Libv2ray() {}

    /**
     * Инициализация окружения ядра: путь к geoip/geosite (assetPath) и ключ
     * XUDP base (deviceId).
     */
    public static void initCoreEnv(String envPath, String key) {
        // stub
    }

    /** Создаёт контроллер ядра с обработчиком колбэков. */
    public static CoreController newCoreController(CoreCallbackHandler handler) {
        return new CoreController();
    }

    /** Версия ядра Xray. */
    public static String checkVersionX() {
        return "stub";
    }

    /** Измерение задержки для конфига. */
    public static long measureOutboundDelay(String configureFileContent, String url) throws Exception {
        throw new UnsupportedOperationException("stub");
    }
}
