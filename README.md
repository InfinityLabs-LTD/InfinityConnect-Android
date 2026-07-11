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
│   └── hysteria2/    # Hysteria2Engine (заглушка)
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

```bash
# 1. gomobile
go install golang.org/x/mobile/cmd/gomobile@latest

# 2. исходники (тег актуальной версии)
git clone --depth 1 --branch v26.7.5 https://github.com/2dust/AndroidLibXrayLite.git
cd AndroidLibXrayLite

# 3. окружение
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.1.12297006

# 4. подготовка и сборка
go mod tidy
gomobile init
# ВАЖНО: -ldflags=-checklinkname=0 обходит несовместимость зависимости
# wlynxg/anet (//go:linkname net.zoneCache) с Go 1.23+.
gomobile bind -target=android -androidapi 26 -ldflags="-checklinkname=0" -o libv2ray.aar .

# 5. подключение
cp libv2ray.aar <проект>/app/libs/libv2ray.aar
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

### Hysteria2 — заглушка

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

## Статус движка

- [x] **Xray (VLESS/Reality/XHTTP) — работает.** Собран `libv2ray.aar`
      (gomobile), поднятие туннеля проверено на эмуляторе: ядро стартует,
      TUN активен, статус «Подключено», статистика и таймер идут, отключение
      чистое. Реальная статистика трафика берётся из
      `queryAllOutboundTrafficStats()`.
- [ ] **Hysteria2** — заглушка `Hysteria2Engine`. Для поддержки: собрать AAR из
      [github.com/apernet/hysteria](https://github.com/apernet/hysteria),
      реализовать `start()` (конфиг из `EngineConfig.Hysteria2`, клиент поверх
      TUN fd — tun2socks не нужен) и `queryStats()`.

## Что осталось доделать вручную

- [ ] Собрать `libv2ray.aar` на машине сборки (см. раздел выше) — бинарник не
      коммитится в git (96 МБ, в `.gitignore`).
- [ ] Интегрировать `hysteria2.aar` (реализовать `Hysteria2Engine`).
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
