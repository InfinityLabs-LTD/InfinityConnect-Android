# hysteria2-mobile

Исходник нативного клиента **Hysteria2** для InfinityConnect: тонкая
gomobile-обёртка над клиентом [apernet/hysteria](https://github.com/apernet/hysteria)
v2 с userspace TCP/IP-стеком [sing-tun](https://github.com/apernet/sing-tun)
поверх TUN fd.

Использованный форк `apernet/sing-tun` собран без gVisor (`WithGVisor=false`),
поэтому обёртка поднимает **системный стек** (`NewStack("system")`). Ему нужен
адрес TUN с префиксом — параметр `tunCidr` в `NewTunnel` (по умолчанию
`10.10.0.1/30`; в первом префиксе должно быть ≥2 адреса: шлюз + клиент).

## Три инварианта, которые нельзя нарушать

Каждый однажды приводил к падению всего приложения.

**1. Владение TUN-дескриптором.** sing-tun оборачивает переданный fd в
`os.NewFile` и закрывает его в `tunIf.Close()` — то есть **забирает во владение**.
Ровно так же ведёт себя libv2ray (Xray). А на стороне Java тем же fd владеет
`ParcelFileDescriptor` и закрывает его сам. Два владельца одного fd —
double-close, который `fdsan` в libc Android 12+ считает фатальным и убивает
процесс.

Поэтому `InfinityVpnService` отдаёт ядру **дубликат**
(`ParcelFileDescriptor.dup().detachFd()`, метод `dupTunFd`) — единая точка для
обоих движков; Go-обёртка принимает fd как свой и повторно его не дублирует.
Kotlin при этом обязан звать `engine.stop()` **до** `tunInterface.close()` —
иначе ядро останется читать закрытый интерфейс.

**2. Адрес TUN.** Системный стек считает своим **первый адрес префикса**
(`inet4ServerAddress`) и биндит на него TCP-форвардер, а следующий (`.2`)
трактует как адрес клиента. Значит `10.10.0.1` из `tunCidr` обязан быть выставлен
на интерфейсе через `VpnService.Builder.addAddress` — константы
`InfinityVpnService.TUN_ADDRESS`/`TUN_PREFIX` и `Hysteria2Engine.TUN_CIDR`
описывают одну и ту же сеть и меняются только вместе. Разъезд даёт
`start tun stack: listen tcp4 …: bind: cannot assign requested address`.

**3. Java-колбэки только с отдельной горутины.** `Protect`, `OnStatus`,
`OnShutdown` — это вызовы Java из Go (cgo-callback). Экспортированные
gomobile-функции (`NewTunnel`) сами вызваны из Java, и их горутина сидит на
системном стеке cgo-вызова; вложенный колбэк оттуда роняет процесс с
`unexpected return pc for runtime.cgocallback` / `fatal error: unknown caller pc`.
Поэтому каждый колбэк уходит через `go func()` + `recover()`: `protectFD`
(`direct.go`) делает это синхронно — стартует горутину и ждёт её, потому что fd
обязан быть защищён до использования сокета; `status`/`OnShutdown` — без
ожидания. Новый Java-колбэк добавлять только по этой схеме.

Ошибки старта стека возвращаются как обычные ошибки (Kotlin показывает их
пользователю), а `Tunnel.Close()` изолирован `recover()` — паника в Go на
teardown тоже убила бы процесс целиком.

Собирается в `app/libs/libhysteria2.aar`, который подключается в
`app/build.gradle.kts`. Java-пакет `hysteria2` (Hysteria2 / Tunnel / Protector /
TunnelCallbackHandler) вызывается из Kotlin-моста
[Hysteria2CoreBridge.kt](../app/src/main/java/com/infinityconnect/vpn/vpn/hysteria2/Hysteria2CoreBridge.kt).

## Файлы

- `hysteria2.go` — API обёртки: `SetContext(Protector)`, `NewTunnel(configJson,
  tunFd, mtu, tunCidr, handler)`, `Tunnel{Close, UplinkBytes, DownlinkBytes}`,
  `Version`.
  Внутри: разбор JSON-конфига (контракт с `Hysteria2ConfigBuilder`), запуск
  hysteria-клиента, TUN-стек sing-tun, форвардинг TCP/UDP через клиент.
- `connfactory.go` — `ConnFactory`, который защищает UDP-сокет QUIC через
  `Protector` (VpnService.protect), иначе трафик клиента зациклится в TUN.
- `fd_unix.go` / `fd_other.go` — `closeFD`: закрытие TUN-дескриптора на путях
  аварийного выхода из `NewTunnel` (владение уже перешло к обёртке, см.
  инвариант 1). Второй файл — заглушка для не-Unix платформ, чтобы пакет
  проверялся типами в IDE на Windows.
- `apply-toolchain-patch.sh` — патч gomobile-тулчейна (см. ниже).
- `build-aar.sh` — сборка AAR.

## Зачем патчить тулчейн

`libv2ray.aar` (Xray) тоже собран gomobile и несёт общий рантайм: классы
`go.Seq*`, `go.Universe`, нативную либу `libgojni.so`, JNI-символы
`Java_go_Seq_*`. Два независимых gomobile-AAR с этим рантаймом **не
сосуществуют** — Gradle падает на `Duplicate class go.Seq` и
`2 files found with path .../libgojni.so`.

`apply-toolchain-patch.sh` делает локальную копию `golang.org/x/mobile` и
переименовывает рантайм hysteria2, чтобы он не пересекался с Xray:

| было | стало |
|------|-------|
| Java-пакет `go` | `hy2go` |
| `libgojni.so` | `libhy2gojni.so` |
| `System.loadLibrary("gojni")` | `loadLibrary("hy2gojni")` |
| JNI `Java_go_Seq_*` | `Java_hy2go_Seq_*` |
| `FindClass "go/Seq..."` | `"hy2go/Seq..."` |

Плюс добавляет `-Wl,-z,max-page-size=16384` — 16КБ-выравнивание LOAD-сегментов
(требование Android 15+).

## Сборка

```bash
# 1. Инструменты
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
export ANDROID_NDK_HOME=/path/to/ndk/27.1.12297006

# 2. Исходники hysteria рядом
git clone --depth 1 https://github.com/apernet/hysteria.git /tmp/hysteria
export HYSTERIA_SRC=/tmp/hysteria

# 3. Патч тулчейна
export XMOBILE_SRC="$(go env GOMODCACHE)/golang.org/x/mobile@<version>"
export XMOBILE_PATCHED=/tmp/xmobile-patched
./apply-toolchain-patch.sh

# 4. Сборка AAR -> app/libs/libhysteria2.aar
./build-aar.sh ../app/libs/libhysteria2.aar
```

AAR ~26 МБ (4 ABI: arm64-v8a, armeabi-v7a, x86, x86_64), в git не коммитится
(см. `.gitignore`, как и `libv2ray.aar`).

## Контракт JSON-конфига

`Hysteria2ConfigBuilder` (Kotlin) → `NewTunnel(configJson, …)`:

```json
{
  "server": "host:port",
  "auth": "…",
  "tls": { "sni": "…", "insecure": false },
  "obfs": { "type": "salamander", "salamander": { "password": "…" } },
  "bandwidth": { "up": "100 mbps", "down": "200 mbps" }
}
```
