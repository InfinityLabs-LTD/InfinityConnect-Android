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
│   ├── xray/         # XrayEngine + мосты к libXray и tun2socks (через рефлексию)
│   └── hysteria2/    # Hysteria2Engine (заглушка)
├── ui/               # Compose: onboarding / auth / home / servers / profile
└── di/               # Hilt-модули (Network, Repository, Token)
```

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

## Интеграция нативных движков (обязательно для реального туннеля)

Нативные библиотеки собираются через **gomobile** (нужен Go-тулчейн) и
подключаются вручную. Положите `.aar`-файлы в `app/libs/` и раскомментируйте
зависимости в [app/build.gradle.kts](app/build.gradle.kts):

```kotlin
implementation(files("libs/libXray.aar"))
implementation(files("libs/hysteria2.aar"))
```

### 1. Xray-core (VLESS / Reality / XHTTP) — libXray

Соберите AAR из [github.com/XTLS/libXray](https://github.com/XTLS/libXray)
(или совместимой обёртки) через gomobile:

```bash
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init
gomobile bind -target=android -androidapi 26 -o libXray.aar github.com/xtls/libxray
```

Затем сверьте имена класса и методов с
[XrayCoreBridge.kt](app/src/main/java/com/infinityconnect/vpn/vpn/xray/XrayCoreBridge.kt):
константы `LIB_CLASS`, `METHOD_RUN`, `METHOD_STOP`. Разные сборки libXray
экспонируют разный API — при необходимости поправьте константы моста.

Также положите `geoip.dat` / `geosite.dat` в каталог `filesDir/xray` (или
доработайте `XrayEngine.ensureDatDir()`), если ваша сборка их требует.

### 2. tun2socks (заворачивание TUN → SOCKS Xray)

Xray работает как локальный SOCKS-прокси; трафик из TUN в него заворачивает
tun2socks (обычно [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)).
Соберите библиотеку и сверьте контракт с
[Tun2SocksBridge.kt](app/src/main/java/com/infinityconnect/vpn/vpn/xray/Tun2SocksBridge.kt)
(`LIB_CLASS`, сигнатуры `startTunnel`/`stopTunnel`).

### 3. Hysteria2 — заглушка

[Hysteria2Engine](app/src/main/java/com/infinityconnect/vpn/vpn/hysteria2/Hysteria2Engine.kt)
пока не интегрирован. Для полной поддержки:
1. Соберите AAR из [github.com/apernet/hysteria](https://github.com/apernet/hysteria)
   через gomobile.
2. Реализуйте `start()`: постройте конфиг из `EngineConfig.Hysteria2`
   (server/auth/tls/obfs/bandwidth) и запустите клиент поверх TUN fd
   (hysteria2 сам читает/пишет TUN — tun2socks не нужен).
3. Пробросьте статистику в `queryStats()`.

## Как работает подключение к серверу

`BuildConnectionUseCase` строит профиль движка по такой стратегии:
1. **subscription_url ключа** — первичный источник (единственный надёжный путь
   для XHTTP и Hysteria2). Тело подписки (base64/список URI) парсится на клиенте
   [SubscriptionParser](app/src/main/java/com/infinityconnect/vpn/domain/subscription/SubscriptionParser.kt).
2. **`/v1/config`** — вспомогательный fallback для VLESS-метаданных (raw_uri →
   поля DTO), когда подписка недоступна.

## Что осталось доделать вручную

- [ ] Собрать и подключить `libXray.aar` + tun2socks-библиотеку, сверить мосты.
- [ ] Собрать и интегрировать `hysteria2.aar` (реализовать `Hysteria2Engine`).
- [ ] Реальная статистика трафика: пробросить stats из libXray в `queryStats()`
      (сейчас показывается только время сессии).
- [ ] Уточнить соответствие индекса сервера из `/v1/config/servers` порядку
      профилей в подписке (при расхождении — матчить по `server_address`).
- [ ] Сгенерировать `gradle-wrapper.jar` и иконки под нужные плотности при
      необходимости (сейчас используется adaptive-иконка).
- [ ] Проверить поведение при отзыве системного разрешения VPN во время сессии.

## Модель приложения (важно)

- **Нет** ручного добавления подписок, сканирования QR, импорта конфигов.
- Ключи загружаются автоматически через `/v1/keys` после логина, при старте и
  pull-to-refresh.
- Клиент знает только **домен сервера**; всё остальное — через `/v1/discovery`.
```
