# Промт: Windows-клиент InfinityConnect (Rust + Tauri, самостоятельный проект)

> Самодостаточное ТЗ для новой сессии. Отвечай на русском, коммить прямо в `main`
> (без веток), сообщения коммитов на русском.
>
> **Это НЕ порт и НЕ KMP.** Мы делаем отдельное, независимое Windows-приложение на
> **Rust + Tauri**. Android-проект (`InfinityConnect-Android`) — только **референс**:
> из него берём проверенную логику, форматы, алгоритмы и поведение сервера. Никакой
> зависимости от Android-кода, никаких `expect/actual`, никакой оглядки на Android-сборку.

---

## 0. Роль и цель

Ты делаешь **Windows-десктоп-клиент InfinityConnect** — самостоятельный продукт.
Функциональность повторяет Android-версию: логин, подписки **Remnawave**, список серверов
в стиле **Happ**, connect/disconnect, статистика, пинг (4 метода), маршрутизация по
приложениям и по сайтам, офлайн-кэш, автовыбор/RawXray, фиолетовая тема.

Стек:
- **UI** — web (React + TypeScript) во фронтенде Tauri.
- **Ядро приложения (backend)** — **Rust** (`src-tauri`): вся логика, сеть, туннель,
  управление процессами ядер.
- **Xray/Hysteria2** — **sidecar-процессы** (`xray.exe` / `hysteria.exe`), как в
  Hiddify/v2rayN. Конфиг — JSON-файл/stdin, туннель — `wintun.dll`, статистика — через
  API-порт ядра.

---

## 1. Где брать «правду» (референс на Android)

Android-код читаем как спецификацию, **не переносим дословно** (другой язык). Ключевые
места для изучения (см. `ARCHITECTURE.md` в этом репозитории):

| Что нужно | Где в Android-проекте |
|---|---|
| Контракт профиля сервера (какие поля у VLESS/Reality/XHTTP/Hy2) | `domain/engine/EngineConfig.kt` |
| Как из профиля собирается JSON-конфиг Xray | `domain/engine/XrayConfigBuilder.kt` |
| Как парсится тело подписки (JSON-массив / base64 / список URI) | `domain/subscription/SubscriptionParser.kt`, `VlessUriParser.kt`, `Hysteria2UriParser.kt` |
| Логика выбора конфига (подписка → fallback `/v1/config`) | `domain/usecase/BuildConnectionUseCase.kt` |
| REST-API сервера (auth/keys/config/user, discovery, тело подписки) | `data/remote/api/*.kt`, DTO в `data/remote/dto/*.kt` |
| Авторизация, рефреш токена при 401, заголовки | `data/remote/AuthInterceptor.kt`, `TokenAuthenticator.kt` |
| HWID устройства (лимит устройств подписки) | `data/local/DeviceIdProvider.kt` |
| Схема пинга (4 метода, режимы, таймаут) | `domain/usecase/PingServerUseCase.kt`, `vpn/xray/XrayProxyPinger.kt`, `domain/model/Ping.kt` |
| Маршрутизация (по сайтам = `routing.rules`, по приложениям = split-tunnel) | `vpn/xray/XrayConfigBuilder.buildRouting`, `vpn/InfinityVpnService.applyPerAppRouting` |
| Офлайн-кэш (discovery/ключи/тела подписок) | `data/local/SubscriptionCacheStore.kt`, memory `offline-mode` |
| RawXray/автовыбор (balancer/WHITE целиком в ядро) | memory `white-autoselect-config` |

> **Важно:** **Xray-конфиг** — это JSON, который одинаков на всех платформах. Логика его
> сборки (`XrayConfigBuilder`) и маршрутизация по сайтам (`routing.rules`) переносятся
> **по смыслу почти даром** — надо повторить генерацию того же JSON на Rust. Это самая
> дешёвая для переноса часть.

---

## 2. Структура проекта (Tauri)

Новый независимый репозиторий/каталог (не внутри Android-проекта):

```
infinity-connect-windows/
├── src/                      Frontend: React + TypeScript
│   ├── screens/              Home / Auth / Profile / Settings(Routing/Ping/About)
│   ├── components/           переиспользуемые виджеты (стиль Happ)
│   ├── theme/                фиолетовая палитра (перенести InfinityColors по значениям)
│   ├── state/                стор (Zustand/Redux) — зеркало VpnStateHolder
│   └── api/                  типы и вызовы Tauri-команд (invoke)
├── src-tauri/                Backend: Rust
│   ├── src/
│   │   ├── api/              HTTP-клиент к серверу (reqwest): auth/keys/config/user/discovery
│   │   ├── subscription/     парсер тела подписки → профили серверов
│   │   ├── engine/           модель профиля (аналог EngineConfig) + сборка Xray JSON
│   │   ├── tunnel/           wintun-адаптер, маршруты ОС, оркестратор туннеля
│   │   ├── sidecar/          запуск/менеджмент xray.exe / hysteria.exe, чтение stats
│   │   ├── ping/             4 метода пинга (proxy через SOCKS-inbound, TCP, ICMP)
│   │   ├── routing/          split-tunnel (WFP) + домены (в Xray routing.rules)
│   │   ├── store/            настройки, токены (DPAPI), офлайн-кэши на %APPDATA%
│   │   ├── device.rs         HWID (MachineGuid из реестра)
│   │   ├── state.rs          источник состояния туннеля → эмит событий во фронт
│   │   └── commands.rs       #[tauri::command] — мост фронт↔бэк
│   ├── binaries/             xray.exe, hysteria.exe, wintun.dll (bundled sidecar)
│   └── tauri.conf.json       конфиг, bundle, требование admin
└── ...
```

**Мост фронт↔бэк:** фронт вызывает Rust через `invoke(command, args)`; состояние туннеля
и статистика приходят обратным потоком через Tauri events (`emit`/`listen`) — это аналог
`VpnStateHolder` как `StateFlow` на Android. Никакой логики во фронте, кроме отображения.

---

## 3. Что делает Rust-бэкенд (по слоям)

- **`api/`** — HTTP к серверу через `reqwest`. Повторить эндпоинты и DTO из Android
  `data/remote`. Discovery по домену → базовый URL. Хранение/рефреш токена (401 →
  refresh), заголовки авторизации, HWID в запросах подписки.
- **`subscription/` + `engine/`** — распарсить тело подписки в список профилей, из профиля
  сгенерировать **тот же Xray JSON**, что и Android `XrayConfigBuilder`. RawXray/автовыбор
  (balancer/WHITE) — пробрасывать **целиком**, не схлопывать в один outbound. XHTTP extra
  (xmux/xPadding/session/seq) — пробрасывать без интерпретации.
- **`tunnel/`** — сердце Windows-специфики:
  - `wintun` через crate `wintun` (обёртка `wintun.dll`): создать адаптер, сессию,
    читать/писать пакеты.
  - настроить маршруты ОС на туннель (IP Helper API / `route`), DNS.
  - оркестратор: поднять wintun → поднять sidecar с конфигом → направить трафик → следить
    за процессом → корректно гасить. Замена `InfinityVpnService`.
  - **kill-switch:** при обрыве ядра блокировать не-VPN трафик (WFP), как флаги
    VpnService на Android.
  - **смена сети** (Wi-Fi ↔ ethernet): слушать события смены маршрута ОС, пересобирать
    underlying-роут (аналог `registerDefaultNetworkCallback` на Android).
- **`sidecar/`** — запуск `xray.exe`/`hysteria.exe` с готовым конфигом (файл в темпе или
  stdin), менеджмент дочернего процесса (Tauri sidecar / `std::process`), чтение
  статистики через API-порт ядра (Xray stats API, hysteria traffic API). Версии ядер —
  синхронно с Android `BuildFlags` (Xray 26.3.27, Hy2 2.x).
- **`ping/`** — 4 метода как в Happ: прокси (GET/HEAD через локальный SOCKS-inbound
  временного sidecar), TCP, ICMP + режимы Default/Double/Keepalive + таймаут. Замеры
  сериализованы. Схема — из Android `PingServerUseCase`/`XrayProxyPinger`.
- **`routing/`** — по сайтам: правила в Xray `routing.rules` (переносится по смыслу
  даром). По приложениям: **WFP-фильтр** по имени/пути процесса — нативного аналога
  Android allow/disallow на Windows нет, это самый нетривиальный пункт.
- **`store/` + `device.rs`** — настройки и офлайн-кэши (discovery/ключи/тела подписок)
  как JSON на `%APPDATA%\InfinityConnect\`; токены шифровать через **DPAPI**
  (`ProtectedData`). HWID — стабильный `MachineGuid` из реестра (совместимый с сервером
  формат: лимит устройств Remnawave считается по нему).

---

## 4. Фронтенд (React/TS)

- Экраны 1:1 с Android: **Home** (hero-кнопка connect + выбранный сервер + аккордеон
  подписок в стиле Happ, бейдж «⚡ Быстрейший»), **Auth**, **Profile**, **Settings** (хаб →
  Маршрутизация / Пинг / О приложении).
- Тема — **фиолетовая** (перенести значения `InfinityColors`). Пинг-пилл красится по
  **качеству**, не по методу. Семантические цвета не трогать.
- Стор (Zustand) отражает состояние туннеля/статистику, приходящие Tauri-событиями.
- Системный трей: статус подключения, connect/disconnect из трея, сворачивание в трей.

---

## 5. Фазы (порядок работ)

**Фаза 0 — Каркас Tauri.** Пустое приложение Tauri (React+Rust), окно, трей, bundling
`xray.exe`/`hysteria.exe`/`wintun.dll` как ресурсов. Мост `invoke`/`emit` работает
end-to-end на тестовой команде.

**Фаза 1 — Аккаунт и подписки.** `api/` + `subscription/` + `store/`: логин, discovery,
загрузка ключей/подписок, HWID, хранение токена (DPAPI). Критерий: логинимся, видим список
серверов (туннеля ещё нет).

**Фаза 2 — MVP-туннель.** `wintun` + `XraySidecar` + `engine/` (генерация Xray JSON):
поднять один VLESS-сервер, connect/disconnect, реальный трафик, статистика тикает.
Критерий: сайт открывается через VPN, скорость/трафик отображаются.

**Фаза 3 — Hysteria2 + RawXray.** `hysteria.exe` sidecar; проброс RawXray/автовыбора
целиком. Паритет по протоколам.

**Фаза 4 — UI-паритет.** Все экраны Home/Auth/Profile/Settings, тема, трей, автозапуск.

**Фаза 5 — Пинг.** 4 метода + режимы + таймаут через временный SOCKS-inbound sidecar.

**Фаза 6 — Маршрутизация.** По сайтам (Xray `routing.rules`) — дёшево. По приложениям —
WFP по процессу (рискованный пункт; допустима заглушка на первой итерации).

**Фаза 7 — Офлайн-кэш + полировка + kill-switch.** Диск-кэши как `offline-mode`.
Kill-switch через WFP. Установщик (Tauri bundler → MSI/NSIS), элевация до admin для
wintun/маршрутов (манифест или helper-служба).

---

## 6. Инварианты (поведение как на Android)

- **Xray-конфиг — единственная «правда» о том, как ходит трафик.** Генерировать тот же
  JSON, что Android `XrayConfigBuilder`.
- **Подписка первична**, `/v1/config` — только fallback (логика `BuildConnectionUseCase`).
- **RawXray** — в ядро целиком (balancer/WHITE/автовыбор), не схлопывать.
- **XHTTP extra** — пробрасывать без интерпретации.
- **HWID** — стабильный, совместимый с сервером (лимит устройств считается по нему).
- **Тема фиолетовая**; пинг-пилл — по качеству. Список серверов раскрыт как в Happ.
- Единый источник состояния туннеля для UI (Rust `state.rs` → Tauri events).

---

## 7. Риски (Windows-специфика)

1. **Права администратора** для wintun и правки маршрутов. Решить рано: манифест
   элевации, либо helper-служба с правами + UI без прав. Влияет на архитектуру процессов.
2. **Split-tunnel по приложениям** — нет нативного аналога Android; только WFP, сложно.
   Не блокировать им MVP.
3. **Kill-switch** — блокировать не-VPN трафик при обрыве ядра (WFP).
4. **Версии ядер** — держать `xray.exe`/`hysteria.exe` в версиях из `BuildFlags`;
   продумать канал обновления бинарников.
5. **DNS-утечки** — направить DNS в туннель, проверить отдельно.

---

## 8. Первый шаг в новой сессии

1. Прочитать `ARCHITECTURE.md` (карта Android-референса) и открыть ключевые файлы из §1.
2. Зафиксировать **контракт профиля сервера**: выписать все поля `EngineConfig` (VLESS/
   Reality/XHTTP/Hy2) и структуру Xray JSON, которую генерирует `XrayConfigBuilder` — это
   основа Rust-модуля `engine/`.
3. Выписать эндпоинты и DTO сервера из `data/remote` — основа Rust-модуля `api/`.
4. Создать каркас Tauri-проекта (Фаза 0) и предложить план Фазы 1, **не начиная логику,
   пока каркас и план не согласованы.**
5. Завести собственный `ARCHITECTURE.md` в новом проекте и вести его с самого начала.
```
