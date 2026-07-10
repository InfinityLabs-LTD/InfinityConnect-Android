package go;

import android.content.Context;

/**
 * СТАБ (compileOnly) go.Seq из gomobile-рантайма.
 *
 * setContext передаёт Android-контекст в Go-слой — через него нативное ядро
 * получает доступ к VpnService.protect (обход собственного трафика ядра мимо
 * TUN). Реальная реализация — в нативном AAR/зависимости gomobile.
 */
public final class Seq {

    private Seq() {}

    public static void setContext(Context context) {
        // stub
    }
}
