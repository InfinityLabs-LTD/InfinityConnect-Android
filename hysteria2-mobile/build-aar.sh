#!/usr/bin/env bash
# Сборка libhysteria2.aar — нативный клиент Hysteria2 для InfinityConnect.
#
# Обёртка (hysteria2.go / connfactory.go) оборачивает клиент apernet/hysteria v2
# + userspace-стек sing-tun (gVisor) поверх TUN fd. gomobile bind генерирует
# Java-пакет hysteria2 (Hysteria2 / Tunnel / Protector / TunnelCallbackHandler),
# который вызывает Kotlin-мост Hysteria2CoreBridge.
#
# ВАЖНО — конфликт с Xray-AAR:
#   libv2ray.aar тоже собран gomobile и несёт рантайм go.Seq + libgojni.so.
#   Два gomobile-AAR с одинаковым рантаймом не сосуществуют (duplicate class
#   go.Seq / duplicate libgojni.so). Поэтому рантайм hysteria2 переименовывается
#   патчем тулчейна gomobile: go -> hy2go, libgojni.so -> libhy2gojni.so,
#   JNI-символы Java_go_Seq_* -> Java_hy2go_Seq_*. Патч применяется к локальной
#   копии golang.org/x/mobile (см. apply-toolchain-patch.sh) и добавляет
#   16КБ-выравнивание LOAD-сегментов (требование Android 15+).
#
# Предпосылки: Go, gomobile+gobind (go install golang.org/x/mobile/cmd/...),
#   Android NDK (проверено на 27.1.12297006), исходники apernet/hysteria рядом.
set -euo pipefail

: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to your NDK path}"
: "${HYSTERIA_SRC:?set HYSTERIA_SRC to a checkout of github.com/apernet/hysteria}"
: "${XMOBILE_PATCHED:?set XMOBILE_PATCHED to the patched golang.org/x/mobile copy}"

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/libhysteria2.aar}"

# go.mod requires local replaces for the hysteria workspace + patched x/mobile.
cd "$HERE"
cat > go.mod.build <<EOF
$(cat go.mod)

replace github.com/apernet/hysteria/core/v2 => $HYSTERIA_SRC/core
replace github.com/apernet/hysteria/extras/v2 => $HYSTERIA_SRC/extras
replace golang.org/x/mobile => $XMOBILE_PATCHED
EOF
mv go.mod go.mod.orig && mv go.mod.build go.mod
trap 'mv -f go.mod.orig go.mod 2>/dev/null || true' EXIT

# Keep x/mobile in the graph (bind runtime import).
cat > tools.go <<'EOF'
//go:build tools
package hysteria2

import _ "golang.org/x/mobile/bind"
EOF

export GOFLAGS=-mod=mod GOWORK=off
go mod tidy

# Reinstall gomobile/gobind FROM the patched toolchain first:
(cd "$XMOBILE_PATCHED" && GOFLAGS=-mod=mod go install ./cmd/gomobile ./cmd/gobind)

gomobile bind \
  -target=android/arm64,android/arm,android/amd64,android/386 \
  -androidapi 26 \
  -o "$OUT" \
  github.com/infinityconnect/hysteria2mobile

echo "built: $OUT"
