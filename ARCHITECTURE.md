# InfinityConnect-Android — карта проекта

> Справочник по файлам, слоям и связям. **Читать перед тем, как разбираться в коде или писать новый.**
> Обновлять при добавлении/переносе файлов и изменении контрактов между слоями.

Android-клиент VPN (Kotlin, Jetpack Compose, Hilt, Retrofit, Coroutines). Два нативных
ядра: **Xray** (VLESS/Reality/XHTTP) и **Hysteria2** (QUIC). Подписки — панель **Remnawave**,
стиль клиента — **Happ** (списки серверов раскрыты у всех подписок сразу).

## Архитектурные слои (Clean Architecture)

```
UI (Compose + ViewModel)
      ↓ usecase
Domain (usecase / model / engine / subscription / repository-интерфейсы)
      ↓ реализации
Data (repository-Impl / remote-API / local-хранилища / DTO)
      ↓
VPN (сервис, движки, мосты к нативным ядрам)  ←── domain.engine.EngineConfig
      ↓ JNI
Нативные модули (libv2ray-stub, libhysteria2-stub, hysteria2-mobile/Go)
```

**Направление зависимостей:** UI → Domain ← Data. VPN-слой зависит от Domain
(`EngineConfig`, `BuildConnectionUseCase`, `RoutingRepository`). DI (Hilt) связывает
интерфейсы с реализациями.

---

## Поток подключения (главный сценарий — держать в голове)

```
HomeScreen (кнопка Connect)
  → HomeViewModel.connect()
    → VpnController.connect(keyId, serverIndex, name)   [фасад, шлёт Intent]
      → InfinityVpnService (ACTION_CONNECT)             [foreground-сервис, TUN]
        → BuildConnectionUseCase(keyId, serverIndex)    [строит EngineConfig]
            → subscriptionRepository.fetch → SubscriptionParser   (первичный путь)
            → configRepository.config → parseSingleUri            (fallback /v1/config)
        → EngineSelector.select(config)                 [Vless/RawXray→Xray, Hy2→Hysteria2]
        → establishTun() → engine.start(service, config, tunFd, mtu)
            → XrayEngine → XrayConfigBuilder → XrayCoreBridge → libv2ray (JNI)
            → Hysteria2Engine → Hysteria2ConfigBuilder → Hysteria2CoreBridge → hysteria2-mobile (Go/JNI)
        → VpnStateHolder.updateState/Stats  →  UI (StateFlow)
```

Состояние туннеля и статистика идут через **`VpnStateHolder`** (singleton), UI читает их из него.

---

## Модули Gradle

| Модуль | Ответственность |
|---|---|
| `app` | Всё приложение (UI, domain, data, vpn). |
| `libv2ray-stub` | **Заглушка** JNI-API Xray-core (`libv2ray`, `go.Seq`). Заменяется реальным AAR при сборке. |
| `libhysteria2-stub` | **Заглушка** JNI-API Hysteria2 (`hysteria2.*`). Заменяется реальным AAR. |
| `hysteria2-mobile/` | Go-исходники gomobile-обёртки Hysteria2 (собираются в AAR отдельно). |

> ⚠️ См. memory: `gomobile-aar-runtime-conflict` — два gomobile-AAR конфликтуют на общем
> `go.Seq`/`libgojni.so`; решено патчем тулчейна.

---

## app/ — карта пакетов

### `ui/` — презентация (Compose + ViewModel)

| Файл | За что отвечает | Связи |
|---|---|---|
| `MainActivity.kt` | Точка входа Activity, хостит `AppNavHost`. | → navigation |
| `InfinityApp.kt` (в корне пакета) | `@HiltAndroidApp`-класс приложения. | Hilt-граф |
| `SplashViewModel.kt` | Стартовый роутинг: discovery → AUTH/HOME/ERROR. | DiscoveryRepository, AuthRepository |
| `navigation/AppNavHost.kt` | Навигационный граф (SPLASH→AUTH→HOME→PROFILE/ROUTING/PING/ABOUT; SETTINGS-хаб остаётся, но главная навигация — боковое меню). `sharedSettingsVm` привязывает VM настроек к SETTINGS либо (при прямом переходе из меню) к HOME. | все экраны |
| `navigation/Routes.kt` | Константы маршрутов. | — |
| `home/HomeScreen.kt` | Главный экран: hero-кнопка (компактная, когда не подключён; под ней — выбранный сервер), подписки — аккордеон через `KeyGroup` (раскрыт только выбранный ключ, остальные свёрнуты со сводкой; бейдж «⚡ Быстрейший» на лучшем сервере). Обёрнут в `ModalNavigationDrawer` — гамбургер открывает боковое меню. | HomeViewModel |
| `home/AppDrawer.kt` | Боковое меню в стиле сайдбара Windows-клиента: лого, пункты-«пилюли» (активный — акцентный градиент; ⚡ Подключение / 🧭 Маршрутизация / 📶 Пинг / 👤 Профиль / ℹ️ О приложении), статус туннеля внизу. | — |
| `home/HomeViewModel.kt` | **Ядро UI-логики:** список ключей, выбор сервера, пинг-all, connect/switch/disconnect. Пинг отменяется при подключении и не запускается при активном туннеле (замеры шли бы через VPN). Фоновая проверка обновления клиента при входе (раз за процесс) → snackbar. | ObserveKeys/SyncKeys/GetServers/PingServer/CheckClientUpdate usecase, VpnController, VpnStateHolder, SettingsStore |
| `home/ConnectHero.kt`, `home/HomeComponents.kt` | Составные Compose-компоненты главного экрана. | — |
| `auth/AuthScreen.kt` + `AuthViewModel.kt` | Экран входа по логину/паролю. | AuthUseCases |
| `profile/ProfileScreen.kt` + `ProfileViewModel.kt` | Аккаунт, подписка, разлогин. | UserRepository, LogoutUseCase |
| `settings/SettingsScreen.kt` | **Хаб-меню настроек:** 3 пункта → Маршрутизация / Настройки пинга / О приложении. | навигация |
| `settings/SettingsViewModel.kt` | VM всех экранов настроек (маршрутизация + пинг + список приложений). Общий инстанс для подэкранов через backstack-entry SETTINGS. | SettingsStore, RoutingRepository |
| `settings/RoutingScreen.kt` | Экран маршрутизации: per-app split-tunnel + домены. Общий режим = ALL (выбор режима и загрузка конфига правил убраны из UI). | SettingsViewModel |
| `settings/PingScreen.kt` | Экран настроек пинга (протокол/режим/таймаут/URL). | SettingsViewModel |
| `settings/AboutScreen.kt` | Экран «О приложении» (версия, ядра, разработчик) + карточка «Обновление» (проверить/скачать/установить APK; автопроверка при открытии). | BuildConfig/BuildFlags, AboutViewModel |
| `settings/AboutViewModel.kt` | VM обновления клиента: check → download (прогресс) → ApkInstaller. | CheckClientUpdate/DownloadClientUpdate usecase |
| `settings/AppPickerScreen.kt` | Экран выбора приложений для per-app (общий VM через backstack-entry SETTINGS). | SettingsViewModel |
| `settings/SettingsCommon.kt` | Общие Compose-элементы экранов настроек (SettingsScaffold/SectionTitle/OptionRow/fieldColors). | — |
| `settings/PingMethodUi.kt` | UI-модель метода пинга. | domain.model.PingMethod |
| `components/Common.kt`, `components/Design.kt` | Переиспользуемые Compose-виджеты. | — |
| `theme/Theme.kt` | Тема Material. | — |
| `util/Browser.kt`, `util/Formatters.kt` | Открытие ссылок, форматирование байт/скорости. | — |
| `util/ApkInstaller.kt` | Запуск системного установщика для скачанного APK (FileProvider `${applicationId}.fileprovider`, cacheDir/updates). | — |

### `domain/` — бизнес-логика (не знает про Android-фреймворки, кроме мелочей)

| Файл | За что отвечает | Связи |
|---|---|---|
| `usecase/BuildConnectionUseCase.kt` | **Ключевой:** строит `EngineConfig` для сервера (подписка → fallback /v1/config). | Keys/Config/SubscriptionRepository, SubscriptionParser |
| `usecase/GetSubscriptionServersUseCase.kt` | Список серверов подписки для UI (+ кэш). | SubscriptionRepository, SubscriptionParser |
| `usecase/PingServerUseCase.kt` | Пинг сервера: прокси (GET/HEAD через ядро), TCP, ICMP. | XrayProxyPinger, SettingsStore |
| `usecase/KeysUseCases.kt` | ObserveKeys / SyncKeys. | KeysRepository |
| `usecase/AuthUseCases.kt` | Login / Logout. | AuthRepository |
| `usecase/ClientUpdateUseCases.kt` | CheckClientUpdate / DownloadClientUpdate (самообновление APK). | ClientUpdateRepository |
| `engine/EngineConfig.kt` | **Контракт профиля сервера** (sealed: `Vless`, `RawXray`, `Hysteria2`) + `Transport`/`Security`. `Transport.Xhttp` несёт сырой `extra` (xmux/xPadding/session/seq/…) — пробрасывается в ядро без интерпретации. | — центральный тип между domain и vpn |
| `engine/XrayConfigBuilder.kt` | Собирает JSON-конфиг Xray (`build` для Vless, `buildRaw` для RawXray). | EngineConfig, Routing |
| `subscription/SubscriptionParser.kt` | **Парсер тела подписки** → `List<EngineConfig>` (JSON-массив Xray / base64 / список URI). | VlessUriParser, Hysteria2UriParser, EngineConfig |
| `subscription/VlessUriParser.kt` | Парсит `vless://` URI. | EngineConfig.Vless |
| `subscription/Hysteria2UriParser.kt` | Парсит `hy2://`/`hysteria2://` URI. | EngineConfig.Hysteria2 |
| `subscription/UriParsing.kt` | Общие хелперы разбора URI. | — |
| `model/Models.kt` | Доменные модели (VpnKey, ServerEntry, UserInfo, SubscriptionServer, VpnProtocol …). | — |
| `model/Result.kt` | `AppResult`/`AppError` (Success/Failure). | — везде |
| `model/Ping.kt` | `PingMethod` (Прокси GET/HEAD/TCP/ICMP), `PingMode` (Default/Double/Keepalive), `PingSettings` (метод+режим+URL+таймаут). Схема как в Happ. | — |
| `model/Routing.kt` | Модель настроек маршрутизации. | — |
| `repository/Repositories.kt` | **Интерфейсы:** Discovery/Auth/User/Keys/Config Repository. | реализации в data/ |
| `repository/RoutingRepository.kt` | Интерфейс настроек маршрутизации. | RoutingRepositoryImpl |
| `repository/SubscriptionRepository.kt` | Интерфейс загрузки/кэша подписки. | SubscriptionRepositoryImpl |
| `repository/ClientUpdateRepository.kt` | Интерфейс обновлений клиента (check/download APK). | ClientUpdateRepositoryImpl |

### `data/` — реализации и I/O

| Файл | За что отвечает | Связи |
|---|---|---|
| `repository/*Impl.kt` | Реализации всех domain-репозиториев. | remote API + local хранилища |
| `repository/Mappers.kt` | DTO → доменные модели. | dto, model |
| `repository/ApiCall.kt` | Обёртка вызова API → `AppResult`. | — |
| `remote/api/InfinityApi.kt` | Основной REST-API (auth/keys/config/user). | Retrofit |
| `remote/api/DiscoveryApi.kt` | Discovery по домену. | — |
| `remote/api/RawApi.kt` | Сырые GET (тело подписки). | — |
| `remote/api/ClientUpdateApi.kt` | Обновления клиента: `/v1/client-updates/android/apk/latest` + скачивание APK (публичные, без Bearer). | Retrofit |
| `remote/dto/*.kt` | DTO ответов (Auth/Config/Discovery/Keys/User). | — |
| `remote/AuthInterceptor.kt`, `InfinityApiInterceptor.kt` | Заголовки, авторизация запросов. | TokenProvider |
| `remote/TokenAuthenticator.kt` | Рефреш токена при 401. | TokenStorage |
| `remote/TokenProvider.kt`, `ApiBaseUrlProvider.kt` | Токен и базовый URL (из discovery). | — |
| `local/SettingsStore.kt` | DataStore настроек (метод пинга и др.). | — читается в HomeVM/SettingsVM |
| `local/SubscriptionCacheStore.kt` | **Офлайн-кэш тел подписок на диске.** | SubscriptionRepositoryImpl |
| `local/TokenStorage.kt`, `KeystoreTokenProvider.kt` | Хранение токенов (шифрование Keystore). | — |
| `local/SessionState.kt`, `DeviceIdProvider.kt` | Сессия, device id (HWID для подписки). | — |

### `di/` — Hilt-модули

| Файл | Что связывает |
|---|---|
| `NetworkModule.kt` | Retrofit, OkHttp, Json, API-интерфейсы. |
| `RepositoryModule.kt` | `@Binds` интерфейсов репозиториев → `*Impl`. |
| `TokenModule.kt` | Провайдеры токенов/хранилища. |

### `vpn/` — VPN-движок и туннель

| Файл | За что отвечает | Связи |
|---|---|---|
| `InfinityVpnService.kt` | **Foreground `VpnService`:** TUN, команды CONNECT/DISCONNECT, статистика, уведомление, per-app split-tunnel, слежение за сетью (`registerDefaultNetworkCallback` + `setUnderlyingNetworks` — туннель переживает Wi-Fi ↔ мобильный). | BuildConnectionUseCase, EngineSelector, VpnStateHolder, RoutingRepository |
| `VpnController.kt` | Фасад для UI: шлёт Intent'ы сервису, `prepareIntent()` разрешения. | InfinityVpnService |
| `EngineSelector.kt` | Выбор движка по `EngineConfig`: Vless/RawXray→Xray, Hy2→Hysteria2. | XrayEngine, Hysteria2Engine |
| `VpnEngine.kt` | **Интерфейс движка** (`supports`/`start`/`stop`/`queryStats`). | реализации ниже |
| `VpnStateHolder.kt` | Singleton-источник состояния/статистики туннеля (StateFlow). | UI, сервис |
| `TunnelState.kt` | `TunnelState` (Disconnected/Connecting/Connected/…) + `TunnelStats`. | — |
| `VpnNotifications.kt` | Канал и построение foreground-уведомления. | — |
| `xray/XrayEngine.kt` | Движок VLESS/RawXray. | XrayConfigBuilder, XrayCoreBridge, RoutingRepository |
| `xray/XrayCoreBridge.kt` | JNI-мост к `libv2ray` (initEnv/start/stop/трафик). | libv2ray-stub |
| `xray/XrayProxyPinger.kt` | **Прокси-пинг** как в Happ: поднимает временный инстанс ядра с локальным SOCKS-inbound (`startLoop`, fd=0) по профилю и гонит через него HTTP (GET/HEAD) своим режимом (Default/Double/Keepalive). Замеры сериализованы. | libv2ray, XrayConfigBuilder |
| `hysteria2/Hysteria2Engine.kt` | Движок Hysteria2. | Hysteria2ConfigBuilder, Hysteria2CoreBridge |
| `hysteria2/Hysteria2ConfigBuilder.kt` | JSON-конфиг для Go-обёртки Hysteria2. | EngineConfig.Hysteria2 |
| `hysteria2/Hysteria2CoreBridge.kt` | JNI-мост к `hysteria2.*` (NewTunnel/Close/трафик). | libhysteria2-stub |

### `BuildFlags.kt`
Флаги сборки (наличие нативных AAR и т.п.).

---

## Нативный слой Hysteria2 (`hysteria2-mobile/`, Go)

| Файл | За что отвечает |
|---|---|
| `hysteria2.go` | **Главная обёртка:** `SetContext`/`NewTunnel`/`Tunnel`; читает пакеты из TUN fd через sing-tun, форвардит TCP/UDP через QUIC-клиент hysteria. |
| `router.go` | Маршрутизация proxy/direct по IP и домену (SNI-сниффинг). |
| `direct.go` | Прямой (bypass) диалер TCP/UDP через protect()-сокеты. |
| `connfactory.go` | `protectedConnFactory` — сокеты клиента вне TUN (`VpnService.protect`). |
| `sni.go` | Сниффинг TLS SNI для доменной маршрутизации. |
| `rudata.go` | Данные правил маршрутизации (RU-специфика). |

> ⚠️ См. memory: `hysteria2-routing-status` — маршрутизация hy2 готова; открытый баг:
> system stack bind 10.10.0.1 + SIGABRT.

---

## Ключевые контракты (что где «правда»)

- **`EngineConfig`** (`domain/engine/EngineConfig.kt`) — единственный тип-мост профиля
  сервера между domain и vpn. Любой новый протокол/транспорт добавляется здесь.
- **`AppResult`/`AppError`** (`domain/model/Result.kt`) — единый способ возврата ошибок
  во всех suspend-функциях. Не бросать исключения из usecase/repository.
- **`VpnStateHolder`** — единственный источник состояния туннеля для UI.
- **Подписка первична:** `BuildConnectionUseCase` берёт конфиг из подписки; `/v1/config` —
  только fallback для VLESS-метаданных.
- **Офлайн-режим (как Happ/INCY):** три диск-кэша переживают перезапуск — discovery
  и ключи (JSON в `SettingsStore`/DataStore), тела подписок (`SubscriptionCacheStore`).
  `KeysRepositoryImpl.key()` берёт ключ из кэша (сеть — только при промахе), поэтому
  список серверов виден и connect строит конфиг без интернета. См. memory `offline-mode`.
- **`RawXray`** — «сложный» конфиг панели (balancer/WHITE/автовыбор) пробрасывается в ядро
  целиком, не схлопывается в один outbound. См. memory `white-autoselect-config`.
- **Маршрутизация 2 уровней:** *по приложениям* (split-tunnel) — на уровне
  `VpnService.Builder` в `InfinityVpnService.applyPerAppRouting` (allow/disallow),
  работает для всех движков. *По сайтам* (домены) — правила `routing.rules` в
  `XrayConfigBuilder.buildRouting`, только Xray (Hy2 доменные правила из UI не
  применяет). Настройки — в `RoutingSettings` (`appMode`/`apps`/`siteMode`/`sites`/`sitePresets`).
  *Пресеты сайтов* (`SitePreset` в `model/Routing.kt`) — готовые наборы доменов с
  фиксированным направлением (direct/proxy), multi-select; складываются с ручным
  списком доменов в `buildRouting` (приоритет: ручные домены → proxy-исключения →
  пресеты → общий режим). Для `RawXray`-конфигов клиентские доменные правила
  подмешиваются ПЕРЕД серверными в `buildRaw`/`mergeClientRulesIntoRaw`:
  direct → freedom-outbound конфига (добавляется `direct-client`, если нет),
  proxy → balancerTag первого balancer'а либо тег первого proxy-outbound.
  Изменение настроек маршрутизации при активном туннеле применяется «на лету»:
  `SettingsViewModel.scheduleTunnelRestart` (дебаунс 1.5 с) переподключает туннель
  к тому же серверу через `VpnStateHolder.activeConnection`.
- **Самообновление (как Windows-клиент):** та же серверная система `/v1/client-updates/*`
  (client_updates.py в проекте InfinityConnect), платформа `android`, «арка» — `apk`
  (universal APK). Проверка — `GET android/apk/latest?current={versionName}&code={versionCode}`,
  сравнение по `version_code`; ответ содержит url/size/sha256. Клиент качает в
  `cacheDir/updates`, сверяет sha256 и открывает системный установщик через
  FileProvider (`REQUEST_INSTALL_PACKAGES`). Скачивания считает сервер по
  `download/{artifact_id}`. Серверная часть — см. `ANDROID_UPDATES_SERVER_PROMPT.md`.
- **Премиум/пинг:** премиум — per-key по `host_name`. Пинг — 4 протокола как в Happ
  (Прокси GET/HEAD через ядро, TCP, ICMP) + режим Default/Double/Keepalive + таймаут;
  прокси-пинг поднимает временный инстанс ядра с SOCKS-inbound (`XrayProxyPinger`).
  См. memory `premium-and-ping-architecture`.

---

## Связанные заметки памяти (`.claude/.../memory/`)
- `gomobile-aar-runtime-conflict` — конфликт двух gomobile-AAR.
- `hysteria2-routing-status` — статус маршрутизации hy2.
- `premium-and-ping-architecture` — премиум и реальный пинг.
- `white-autoselect-config` — автовыбор «LTE Все операторы» / RawXray.
