package libv2ray;

/**
 * СТАБ (compileOnly) интерфейса обратных вызовов ядра из AndroidLibXrayLite.
 *
 * Реальную реализацию предоставляет нативный AAR (libv2ray), собранный через
 * gomobile. Этот стаб нужен ТОЛЬКО для компиляции — в рантайме используется
 * класс из AAR. Сигнатуры соответствуют gomobile-обёртке Go-интерфейса
 * CoreCallbackHandler { Startup()int; Shutdown()int; OnEmitStatus(int,string)int }.
 */
public interface CoreCallbackHandler {
    long startup();

    long shutdown();

    long onEmitStatus(long l, String s);
}
