# Infinity Connect — Android-клиент

Нативный Android-клиент VPN-сервиса **Infinity Connect** (панель Remnawave).
Закрытый фирменный клиент: единственный источник ключей — аккаунт пользователя
в Infinity Connect. Сторонние подписки/QR/импорт конфигов не поддерживаются
by design.

## Технологический стек

- **Kotlin**, minSdk 26 (Android 8.0), targetSdk 35
- **Jetpack Compose** + Material 3
- Архитектура **MVVM** + слои `data / domain / ui`, DI через **Hilt**
- **Retrofit + OkHttp + kotlinx.serialization**
- **DataStore** (настройки) + **EncryptedSharedPreferences/Keystore** (токены)
- **VpnService** + два движка: **Xray-core** (VLESS/Reality/XHTTP) и **Hysteria2**

## Структура проекта

```
app/src/main/java/com/infinityconnect/vpn/
├── data/
│   ├── remote/        # Retrofit API, DTO, interceptors (Bearer, refresh, base-url)
│   ├── local/         # TokenStorage (Keystore), SettingsStore (DataStore), SessionState
│   └── repository/    # реализации репозиториев + мапперы
├── domain/
│   ├── model/         # доменные модели, AppResult/AppError
│   ├── repository/    # интерфейсы репозиториев
│   ├── subscription/  # ПАРСЕР ПОДПИСКИ: VLESS/hy2/base64 → EngineConfig
│   ├── engine/        # EngineConfig + XrayConfigBuilder (JSON Xray)
│   └── usecase/       # LoginAndSync, SyncKeys, BuildConnection, …
├── vpn/
│   ├── VpnEngine / EngineSelector / VpnStateHolder / VpnController
│   ├── InfinityVpnService   # TUN + foreground + выбор движка
│   ├── xray/         # XrayEngine + мост к libv2ray (Xray-core)
│   └── hysteria2/    # Hysteria2Engine + мост + конфиг-билдер (libhysteria2.aar)
├── ui/               # Compose: auth / home (ключи+серверы, стиль Happ) / profile
└── di/               # Hilt-модули (Network, Repository, Token)
```

> Домен сервера фиксирован ([BuildFlags.SERVER_DOMAIN]) — экрана ввода адреса и
> онбординга нет. При запуске discovery выполняется автоматически, пользователь
> вводит только логин и пароль.

## Сборка

### Требования
- Android Studio (последняя стабильная) или Gradle 8.11+
- JDK 17
- Android SDK 35

### Первый запуск
1. Откройте проект в Android Studio — он подтянет зависимости и создаст
   `local.properties` с `sdk.dir`.
2. Если собираете из консоли, сгенерируйте Gradle wrapper jar (однократно):
   ```
   gradle wrapper --gradle-version 8.11.1
   ```
   (файл `gradle/wrapper/gradle-wrapper.jar` не входит в репозиторий как бинарник).
3. Соберите: `./gradlew :app:assembleDebug`

Проект **компилируется без нативных AAR** (движки вызываются через рефлексию и
корректно сообщают об отсутствии библиотек). Для реального VPN нужны AAR — см.
ниже.

## Интеграция нативного движка Xray (обязательно для реального туннеля)

Движок VLESS/Reality/XHTTP работает на нативной библиотеке **libv2ray**
(AndroidLibXrayLite — обёртка Xray-core, та же, что в v2rayNG). AAR собирается
через gomobile и кладётся в `app/libs/libv2ray.aar`.

Код компилируется и без AAR (против стаб-модуля `:libv2ray-stub`), но реальный
туннель требует настоящий AAR. Переключение — в
[app/build.gradle.kts](app/build.gradle.kts):

```kotlin
// С реальным AAR (по умолчанию):
implementation(files("libs/libv2ray.aar"))
// Без AAR (только компиляция, туннель не поднимется):
// compileOnly(project(":libv2ray-stub"))
```

### Сборка libv2ray.aar (проверенная последовательность)

Требуется: **Go 1.24+**, **Android NDK** (r27b проверен), **JDK 17+**.

> ⚠️ **Собирать ЧИСТЫМ gomobile, не патченным для Hysteria2.** Если на машине
> уже стоит тулчейн из `hysteria2-mobile/apply-toolchain-patch.sh`, в
> `~/go/bin/gomobile` лежит патченная версия: она соберёт Xray как hy2-рантайм
> (`libhy2gojni.so` + version-script `hy2_exports.map`) и подставит свой
> `-ldflags`, перебив `-checklinkname=0` — сборка упадёт на
> `link: github.com/wlynxg/anet: invalid reference to net.zoneCache`.
> Поэтому ставим чистый gomobile в отдельный GOBIN и кладём его первым в PATH.

```bash
# 1. чистый gomobile в отдельный GOBIN (не перетирает глобальный hy2-патченный)
export GOBIN=/tmp/cleanbin
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
export PATH="$GOBIN:$PATH"   # обязательно ПЕРЕД ~/go/bin
# ...и всё равно вызывать по АБСОЛЮТНОМУ пути ($GOBIN/gomobile): в Git Bash на
# Windows правки PATH внутри скрипта не всегда перебивают ~/go/bin/gomobile.exe.
# Проверка: `gomobile version` должен печатать путь из $GOBIN, не из ~/go/bin.

# 2. исходники (тег актуальной версии)
git clone --depth 1 --branch v26.7.28 https://github.com/2dust/AndroidLibXrayLite.git
cd AndroidLibXrayLite

# 3. окружение
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.1.12297006

# 4. подготовка и сборка
go mod tidy
gomobile init
# ВАЖНО, два флага в одном -ldflags:
#  * -checklinkname=0 обходит несовместимость зависимости wlynxg/anet
#    (//go:linkname net.zoneCache) с Go 1.23+;
#  * -extldflags=-Wl,-z,max-page-size=16384 даёт 16КБ-выравнивание LOAD-сегментов.
#    БЕЗ него gomobile выравнивает на 4КБ, и Android 15+ показывает
#    "PageSizeMismatchDialog", а на устройствах с 16КБ-страницами .so не грузится.
gomobile bind -target=android -androidapi 26 \
  -ldflags="-checklinkname=0 -extldflags=-Wl,-z,max-page-size=16384" \
  -o libv2ray.aar .

# 5. подключение
cp libv2ray.aar <проект>/app/libs/libv2ray.aar

# 6. проверка выравнивания (должно быть 0x4000, не 0x1000):
#    unzip -o libv2ray.aar jni/arm64-v8a/libgojni.so
#    llvm-readelf --program-headers jni/arm64-v8a/libgojni.so | grep -A1 LOAD
```

API моста (`libv2ray.CoreController`, `Libv2ray.newCoreController`, `go.Seq`) —
в [XrayCoreBridge.kt](app/src/main/java/com/infinityconnect/vpn/vpn/xray/XrayCoreBridge.kt);
сигнатуры-стабы — в модуле `libv2ray-stub`. При смене версии AAR сверьте их
через `javap -cp classes.jar libv2ray.CoreController`.

Замечания по конфигу (учтены в [XrayConfigBuilder.kt](app/src/main/java/com/infinityconnect/vpn/domain/engine/XrayConfigBuilder.kt)):
- inbound типа `tun` (fd передаётся в `startLoop`, встроенный tun2socks —
  отдельная библиотека не нужна);
- routing использует явные CIDR приватных сетей вместо `geoip:private`, чтобы
  не требовать файл `geoip.dat`;
- XUDP base key (`initCoreEnv`) — ровно 32 байта (иначе ядро паникует),
  см. `XrayCoreBridge.xudpBaseKey`.

### Hysteria2

[Hysteria2Engine](app/src/main/java/com/infinityconnect/vpn/vpn/hysteria2/Hysteria2Engine.kt)
реализован по той же схеме, что и Xray: прямые вызовы gomobile-обёртки из
[Hysteria2CoreBridge.kt](app/src/main/java/com/infinityconnect/vpn/vpn/hysteria2/Hysteria2CoreBridge.kt),
конфиг клиента строит
[Hysteria2ConfigBuilder.kt](app/src/main/java/com/infinityconnect/vpn/vpn/hysteria2/Hysteria2ConfigBuilder.kt).
Сигнатуры-стабы — в модуле `libhysteria2-stub` (пакет `hysteria2`).

Нативный AAR **собирается из исходников** обёртки в каталоге
[hysteria2-mobile/](hysteria2-mobile/) — тонкий gomobile-слой над клиентом
[apernet/hysteria](https://github.com/apernet/hysteria) v2 + userspace-стек
sing-tun (системный стек) поверх TUN fd. Инструкция и рецепт сборки — в
[hysteria2-mobile/README.md](hysteria2-mobile/README.md).

Стек: форк `apernet/sing-tun` собран без gVisor (`WithGVisor=false`), поэтому
обёртка использует **системный стек** (`NewStack("system")`). Ему нужен адрес
TUN с префиксом — передаётся параметром `tunCidr` (по умолчанию `10.10.0.1/30`,
согласован с `VpnService.Builder.addAddress("10.10.0.2", 30)`).

Ключевой момент: `libv2ray.aar` (Xray) тоже собран gomobile и несёт общий
рантайм (`go.Seq`, `libgojni.so`). Чтобы два AAR не конфликтовали (duplicate
class / duplicate .so), рантайм hysteria2 переименован патчем тулчейна
(`go`→`hy2go`, `libgojni.so`→`libhy2gojni.so`, JNI `Java_go_Seq_*`→
`Java_hy2go_Seq_*`) + 16КБ-выравнивание LOAD (Android 15+). См.
`hysteria2-mobile/apply-toolchain-patch.sh`.

API AAR (сверено через `javap`): `Hysteria2.setContext(Protector)`,
`Hysteria2.newTunnel(configJson, tunFd, mtu, tunCidr, handler)`,
`Tunnel.close/uplinkBytes/downlinkBytes`. Клиент сам читает/пишет TUN
(tun2socks не нужен); свой UDP-сокет QUIC защищает через `Protector`
(VpnService.protect). Профиль — из `EngineConfig.Hysteria2`
(server/auth/tls/obfs/bandwidth).

Профиль Hysteria2 приходит двумя путями: как `hy2://` URI
([Hysteria2UriParser](app/src/main/java/com/infinityconnect/vpn/domain/subscription/Hysteria2UriParser.kt))
или как outbound `"protocol":"hysteria"` внутри JSON-конфига панели (Remnawave)
— [SubscriptionParser](app/src/main/java/com/infinityconnect/vpn/domain/subscription/SubscriptionParser.kt)
разбирает `hysteriaSettings`/`tlsSettings` в `EngineConfig.Hysteria2`.

Переключение стаб ↔ AAR — в `app/build.gradle.kts`:
`implementation(files("libs/libhysteria2.aar"))` (реальный) либо
`compileOnly(project(":libhysteria2-stub"))` (только компиляция без AAR).

## Как работает подключение к серверу

`BuildConnectionUseCase` строит профиль движка по такой стратегии:
1. **subscription_url ключа** — первичный источник (единственный надёжный путь
   для XHTTP и Hysteria2). Тело подписки (base64/список URI) парсится на клиенте
   [SubscriptionParser](app/src/main/java/com/infinityconnect/vpn/domain/subscription/SubscriptionParser.kt).
2. **`/v1/config`** — вспомогательный fallback для VLESS-метаданных (raw_uri →
   поля DTO), когда подписка недоступна.

## Статус движка

- [x] **Xray (VLESS/Reality/XHTTP) — работает.** Собран `libv2ray.aar`
      (gomobile), поднятие туннеля проверено на эмуляторе: ядро стартует,
      TUN активен, статус «Подключено», статистика и таймер идут, отключение
      чистое. Реальная статистика трафика берётся из
      `queryAllOutboundTrafficStats()`.
- [x] **Hysteria2 — работает.** Собран `libhysteria2.aar` (gomobile-обёртка над
      apernet/hysteria + sing-tun, см. [hysteria2-mobile/](hysteria2-mobile/)),
      4 ABI, 16КБ-выравнивание. Движок `Hysteria2Engine` (мост, конфиг-билдер,
      статистика, обработка разрыва) поднимает TUN через нативный клиент. На
      эмуляторе (x86_64, Android 15) APK ставится и запускается, `libhy2gojni.so`
      грузится без конфликта с Xray-рантаймом.

## Что осталось доделать вручную

- [ ] Собрать `libv2ray.aar` на машине сборки (см. раздел выше) — бинарник не
      коммитится в git (96 МБ, в `.gitignore`).
- [ ] Пересобрать `app/libs/libhysteria2.aar` при обновлении версии hysteria/NDK
      (`hysteria2-mobile/build-aar.sh`) — бинарник в git не коммитится.
- [ ] Уточнить соответствие индекса сервера из `/v1/config/servers` порядку
      профилей в подписке (при расхождении — матчить по `server_address`).
- [ ] Сгенерировать `gradle-wrapper.jar` и иконки под нужные плотности при
      необходимости (сейчас используется adaptive-иконка).
- [ ] `network_security_config.xml` разрешает cleartext для `10.0.2.2`/
      `localhost` (тест против локального сервера) — на релиз можно убрать.
- [ ] Проверить поведение при отзыве системного разрешения VPN во время сессии.

## Модель приложения (важно)

- **Нет** ручного добавления подписок, сканирования QR, импорта конфигов.
- Ключи загружаются автоматически через `/v1/keys` после логина, при старте и
  pull-to-refresh.
- Клиент знает только **домен сервера**; всё остальное — через `/v1/discovery`.
