#!/usr/bin/env bash
# Патч тулчейна gomobile (golang.org/x/mobile) для сборки libhysteria2.aar
# рядом с libv2ray.aar без конфликта общего gomobile-рантайма.
#
# Переименовывает рантайм hysteria2:
#   Java-пакет  go        -> hy2go
#   нативная либ libgojni.so -> libhy2gojni.so
#   JNI-символы Java_go_Seq_* -> Java_hy2go_Seq_*, FindClass "go/..." -> "hy2go/..."
# и добавляет 16КБ-выравнивание LOAD-сегментов (Android 15+).
#
# Использование:
#   XMOBILE_SRC="$(go env GOMODCACHE)/golang.org/x/mobile@VERSION" \
#   XMOBILE_PATCHED=/path/to/xmobile-patched ./apply-toolchain-patch.sh
set -euo pipefail
: "${XMOBILE_SRC:?set XMOBILE_SRC to the pristine x/mobile module dir}"
: "${XMOBILE_PATCHED:?set XMOBILE_PATCHED to the destination for the patched copy}"

rm -rf "$XMOBILE_PATCHED"
cp -r "$XMOBILE_SRC" "$XMOBILE_PATCHED"
chmod -R u+w "$XMOBILE_PATCHED"
D="$XMOBILE_PATCHED"

# 1) generator: runtime pkg default go -> hy2go, and the import literal.
sed -i 's/\t\treturn "go"/\t\treturn "hy2go"/' "$D/bind/genjava.go"
sed -i 's/import go\.Seq;/import hy2go.Seq;/' "$D/bind/genjava.go"
# 2) gobind: static Seq.java is written to java/go -> java/hy2go.
sed -i 's#filepath.Join("java", "go", javaFile)#filepath.Join("java", "hy2go", javaFile)#' "$D/cmd/gobind/gen.go"
# 3) .so name + 16KB alignment linker flag.
sed -i 's#abi + "/libgojni.so"#abi + "/libhy2gojni.so"#' "$D/cmd/gomobile/bind_androidapp.go"
sed -i 's#toolchain.abi, "libgojni.so"#toolchain.abi, "libhy2gojni.so"#' "$D/cmd/gomobile/bind_androidapp.go"

# 3a) Version script: прячем ВСЁ, кроме JNI-точек входа.
#     КРИТИЧНО для сосуществования с libgojni.so (Xray). Оба .so несут одинаково
#     названную внутреннюю механику cgo — crosscall2, _cgo_topofstack, _cgo_panic,
#     x_cgo_sigaction, go_seq_* и т.д. (84 общих символа). Все они глобальны, и
#     динамический линковщик разрешает их в библиотеку, загруженную ПЕРВОЙ:
#     переход Java→Go одного ядра уходит в рантайм другого, процесс падает
#     ("unexpected return pc for runtime.cgocallback" / "unknown caller pc" /
#     "gcBgMarkWorker: blackening not enabled") при работе ВТОРОГО ядра за сессию.
#     Переименовать их нельзя — они генерируются рантаймом Go; поэтому делаем их
#     локальными, наружу оставляем только Java_* и JNI_OnLoad.
#     Наружу оставляем только JNI-точки входа Java_* (JNI_OnLoad gomobile не
#     определяет — перечислять его нельзя, ld.lld падает на несуществующем
#     символе).
cat > "$D/cmd/gomobile/hy2_exports.map" <<'MAPEOF'
{
  global:
    Java_*;
  local:
    *;
};
MAPEOF
# Оба -Wl-флага передаём ОДНИМ токеном без пробела: значение -extldflags go build
# разбивает по пробелам, а кавычки внутри -ldflags= до линковщика не доживают
# ("unterminated \" string"). Запятая внутри -Wl,... — штатный разделитель
# подфлагов, поэтому склейка через запятую эквивалентна двум отдельным -Wl.
sed -i 's#"-buildmode=c-shared",#"-buildmode=c-shared",\n\t\t"-ldflags=-extldflags=" + hy2ExtLdFlags(),#' "$D/cmd/gomobile/bind_androidapp.go"
# Хелпер с флагами внешнего линковщика. Путь к version script записывается в
# нативном для платформы виде (на Windows — C:/..., иначе clang его не найдёт).
# Флаги отдаются одной строкой: go build сам передаёт её в -extldflags целиком.
MAP_PATH="$D/cmd/gomobile/hy2_exports.map"
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) MAP_PATH="$(cygpath -m "$MAP_PATH")" ;;
esac
cat >> "$D/cmd/gomobile/bind_androidapp.go" <<GOEOF

// hy2ExtLdFlags — флаги внешнего линковщика для libhy2gojni.so:
//   - max-page-size=16384: 16КБ-выравнивание LOAD-сегментов (Android 15+);
//   - version-script: скрывает внутренние символы cgo-рантайма. Без него
//     libhy2gojni.so и libgojni.so (Xray) разделяют crosscall2/_cgo_*/go_seq_*
//     и ломают друг другу cgo-переходы — процесс падает при работе второго
//     за сессию ядра.
func hy2ExtLdFlags() string {
	return "-Wl,-z,max-page-size=16384,--version-script=$MAP_PATH"
}
GOEOF
# 4) Seq.java: package + loadLibrary + Universe import.
sed -i 's/^package go;/package hy2go;/' "$D/bind/java/Seq.java"
sed -i 's/System.loadLibrary("gojni");/System.loadLibrary("hy2gojni");/' "$D/bind/java/Seq.java"
sed -i 's/import go\.Universe;/import hy2go.Universe;/' "$D/bind/java/Seq.java"
# 5) JNI C support: symbol names + FindClass paths.
sed -i 's/Java_go_Seq_/Java_hy2go_Seq_/g; s#Lgo/Seq#Lhy2go/Seq#g; s#"go/Seq#"hy2go/Seq#g' "$D/bind/java/seq_android.c.support"
sed -i 's/Java_go_Seq_setContext/Java_hy2go_Seq_setContext/' "$D/bind/java/context_android.c"
sed -i 's#"go/Seq"#"hy2go/Seq"#g' "$D/bind/java/seq_android.h"

# 6) Экспортируемые C-символы рантайма: DestroyRef/IncGoRef.
#    КРИТИЧНО. Оба .so определяют их глобально и с одинаковыми именами, поэтому
#    динамический линковщик разрешает вызовы в ту библиотеку, что загрузилась
#    первой. В результате hysteria2-код зовёт функции рантайма Xray (и наоборот),
#    что ломает cgo-переходы и GC: процесс падает с
#    "unexpected return pc for runtime.cgocallback", "unknown caller pc" или
#    "gcBgMarkWorker: blackening not enabled" — при втором запущенном за сессию
#    ядре, в любом порядке. Переименование делает символы непересекающимися.
sed -i 's/^\/\/export DestroyRef$/\/\/export hy2DestroyRef/; s/^func DestroyRef(/func hy2DestroyRef(/' "$D/bind/java/seq_android.go.support"
sed -i 's/^\/\/export IncGoRef$/\/\/export hy2IncGoRef/; s/^func IncGoRef(/func hy2IncGoRef(/' "$D/bind/seq.go.support"
# Вызовы из C-поддержки и объявления в заголовках — под новые имена.
sed -i 's/\bDestroyRef(/hy2DestroyRef(/g; s/\bIncGoRef(/hy2IncGoRef(/g' "$D/bind/java/seq_android.c.support"
for f in "$D"/bind/java/seq_android.h "$D"/bind/java/seq.h; do
  [ -f "$f" ] && sed -i 's/\bDestroyRef(/hy2DestroyRef(/g; s/\bIncGoRef(/hy2IncGoRef(/g' "$f"
done

echo "patched toolchain at: $D"
