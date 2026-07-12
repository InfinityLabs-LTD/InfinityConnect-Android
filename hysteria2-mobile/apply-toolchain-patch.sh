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
sed -i 's#"-buildmode=c-shared",#"-buildmode=c-shared",\n\t\t"-ldflags=-extldflags=-Wl,-z,max-page-size=16384",#' "$D/cmd/gomobile/bind_androidapp.go"
# 4) Seq.java: package + loadLibrary + Universe import.
sed -i 's/^package go;/package hy2go;/' "$D/bind/java/Seq.java"
sed -i 's/System.loadLibrary("gojni");/System.loadLibrary("hy2gojni");/' "$D/bind/java/Seq.java"
sed -i 's/import go\.Universe;/import hy2go.Universe;/' "$D/bind/java/Seq.java"
# 5) JNI C support: symbol names + FindClass paths.
sed -i 's/Java_go_Seq_/Java_hy2go_Seq_/g; s#Lgo/Seq#Lhy2go/Seq#g; s#"go/Seq#"hy2go/Seq#g' "$D/bind/java/seq_android.c.support"
sed -i 's/Java_go_Seq_setContext/Java_hy2go_Seq_setContext/' "$D/bind/java/context_android.c"
sed -i 's#"go/Seq"#"hy2go/Seq"#g' "$D/bind/java/seq_android.h"

echo "patched toolchain at: $D"
