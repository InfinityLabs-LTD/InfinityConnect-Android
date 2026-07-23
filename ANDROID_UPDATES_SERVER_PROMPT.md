# Промт: серверная часть обновлений Android-клиента (InfinityConnect)

> Промт для выполнения в проекте сервера: `C:\Repositories\InfinityConnect`
> (Python/Flask, `src/shop_bot/webhook_server/`). Android-клиент уже готов и ждёт
> контракт, описанный ниже — менять его нельзя, только реализовать.

## Контекст

В сервере уже есть система обновлений **Windows-клиента** (Tauri):

- `src/shop_bot/webhook_server/client_updates.py` — blueprint `/v1/client-updates/*`:
  - `GET /v1/client-updates/{platform}/{arch}/latest?current={semver}` — формат Tauri latest.json, 204 если актуально;
  - `GET /v1/client-updates/download/{artifact_id}` — стриминг файла + атомарный инкремент `download_count` (Range-докачка не считается новым скачиванием);
  - `save_artifact_upload` / `delete_release_with_files` — логика админки.
- БД (`database.py`, `_ensure_client_updates_tables`): таблицы `client_releases`
  (id, version, platform, notes, pub_date, published, created_at, created_by; уникальный
  индекс platform+version) и `client_artifacts` (id, release_id, arch, file_name,
  file_size, sha256, signature NOT NULL, storage_path, download_count).
- Админка: `templates/admin_client_updates.html` + роуты `admin_client_updates_*`
  в `app.py` (~строка 15290): создание релиза с загрузкой артефактов, публикация/снятие,
  удаление черновиков, показ числа скачиваний.
- Хранилище: `storage/client-updates/{platform}/{version}/`.
- Тесты: `tests/test_client_updates.py`.

## Задача

Расширить эту же систему для **Android** (платформа `android`), переиспользуя
максимум существующего кода. НЕ создавать параллельную подсистему.

### 1. БД

- В `client_releases` добавить колонку `version_code INTEGER` (nullable; для
  windows-релизов остаётся NULL). Миграция — идемпотентный `ALTER TABLE` в
  `_ensure_client_updates_tables` в стиле остальных миграций проекта
  (`try/except` на существующую колонку).
- Для android-релизов `version_code` обязателен и уникален среди android-релизов
  (валидация в админке); монотонный рост не форсировать — сервер всё равно отдаёт
  максимальный `version_code` строго больше клиентского.
- `arch` android-артефакта — литерал `apk` (universal APK). Расширить допустимые
  arch: для платформы `android` разрешён только `apk`; для desktop — прежние
  `x86_64`/`aarch64`.
- `signature` для android-артефактов не используется (APK подписан подписью
  приложения, Android сам проверяет при установке) — хранить пустую строку;
  снять требование «signature обязательна» только для платформы `android`.

### 2. Публичный API (в `client_updates.py`)

Новый эндпоинт (не трогая Tauri-формат существующего):

```
GET /v1/client-updates/android/apk/latest?current={semver}&code={versionCode}
```

- `current` — versionName клиента (semver, валидировать `is_valid_semver`);
  `code` — целое ≥ 0 (невалидное → 400).
- Найти опубликованный android-релиз с максимальным `version_code`, строго
  большим `code`. Если нет — `204` c `Cache-Control: public, max-age=300`.
- Ответ `200` (тоже с кэшем 5 минут):

```json
{
  "version": "1.2.0",
  "version_code": 10200,
  "notes": "Что нового…",
  "pub_date": "2026-07-23T12:00:00Z",
  "apk": {
    "url": "https://host/v1/client-updates/download/123",
    "size": 45678901,
    "sha256": "hex…"
  }
}
```

- `url` строить через существующий `_public_base_url()`.
- Скачивание идёт через существующий `download/{artifact_id}` — он уже стримит с
  Range и считает скачивания; менять его не нужно (кроме, при необходимости,
  mimetype `application/vnd.android.package-archive` для `.apk`).
- В `_ALLOWED_ARTIFACT_SUFFIXES` добавить `.apk`.
- Важно: существующий роут `/{platform}/{arch}/latest` при `platform=android`
  должен либо продолжать работать (не ломать), либо отдавать 404 — android-клиент
  его не использует.

### 3. Админка

В `admin_client_updates.html` и роутах `admin_client_updates_*`:

- В форме создания релиза — выбор платформы уже есть/добавить `android`.
  При платформе `android`:
  - обязательное поле `version_code` (целое > 0, уникальное среди android-релизов);
  - один файл-артефакт `.apk` (arch фиксирован `apk`), поле подписи скрыто/не требуется;
  - sha256 и размер сервер считает сам (уже реализовано в `save_artifact_upload`).
- В таблице релизов показывать `version_code` для android и суммарный
  `download_count` (уже агрегируется в `list_client_releases`).
- Публикация/снятие/удаление — существующие роуты без изменений (проверить, что
  публикация android-релиза требует наличия apk-артефакта — по аналогии с текущей
  проверкой «есть артефакты»).

### 4. Тесты (`tests/test_client_updates.py`, дополнить)

- 204 когда android-релизов нет / клиент актуален (по `code`).
- 200 с корректным телом при доступном обновлении; выбор максимального
  `version_code` из нескольких опубликованных.
- 400 на невалидные `current`/`code`.
- Черновики (published=0) не отдаются.
- Скачивание apk инкрементирует `download_count`; Range-докачка — нет.
- Загрузка apk через админку: подпись не требуется, sha256/size посчитаны.

### 5. Что НЕ делать

- Не менять контракт Windows/Tauri-эндпоинтов и формат latest.json.
- Не вводить новую таблицу — использовать `client_releases`/`client_artifacts`.
- Не добавлять авторизацию на публичные эндпоинты (обновление доступно до логина).

## Контракт клиента (уже реализован в Android, для сверки)

- `ClientUpdateApi.latest("client-updates/android/apk/latest", current, code)` —
  ожидает 200 c JSON выше или 204.
- Поля JSON: `version`, `version_code`, `notes?`, `pub_date?`, `apk{url,size,sha256?}`.
- Клиент сверяет `sha256` (lowercase hex) после скачивания; если поле
  отсутствует/пустое — не сверяет.
- `apk.url` — абсолютный URL; клиент делает по нему обычный GET.
