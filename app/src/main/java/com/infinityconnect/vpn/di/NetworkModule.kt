package com.infinityconnect.vpn.di

import com.infinityconnect.vpn.BuildConfig
import com.infinityconnect.vpn.data.remote.ApiBaseUrlProvider
import com.infinityconnect.vpn.data.remote.AuthInterceptor
import com.infinityconnect.vpn.data.remote.InfinityApiInterceptor
import com.infinityconnect.vpn.data.remote.TokenAuthenticator
import com.infinityconnect.vpn.data.remote.TokenProvider
import com.infinityconnect.vpn.data.remote.api.ClientUpdateApi
import com.infinityconnect.vpn.data.remote.api.DiscoveryApi
import com.infinityconnect.vpn.data.remote.api.InfinityApi
import com.infinityconnect.vpn.data.remote.api.RawApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt-модуль сетевого слоя. Собирает три OkHttp-клиента:
 *  - "discovery" — абсолютные @Url-запросы без Bearer ([DiscoveryApi], [RawApi]);
 *  - "api"       — относительные запросы API с авто-Bearer, refresh и
 *                  динамическим api_base_url ([InfinityApiInterceptor]);
 *  - "download"  — скачивание APK обновления (см. комментарий у провайдера).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Плейсхолдерный базовый URL: реальный хост подставляет InfinityApiInterceptor.
    private const val PLACEHOLDER_BASE = "https://placeholder.invalid/"

    // ---------------------------------------------------------------------
    // Таймауты
    //
    // По умолчанию OkHttp даёт connect/read/write по 10 с, а callTimeout = 0
    // (без ограничения). На практике этого мало: у VPN-сервиса в РФ типовой
    // сценарий — провайдер/DPI не отбивает соединение RST'ом, а молча его
    // «подвешивает». Тогда TCP-сессия жива, данных нет, и запрос висит,
    // пока ОС не решит иначе; вместе с ним висит экран подключения.
    //
    // Поэтому задаём явные значения и, главное, общий потолок на вызов
    // (callTimeout) — он единственный ограничивает суммарное время с учётом
    // DNS, TLS-хендшейка, редиректов и РЕТРАЕВ (OkHttp сам повторяет запрос
    // на другой IP из DNS-ответа, и per-stage таймауты при этом стартуют
    // заново — без callTimeout «зависание» может умножаться на число IP).
    // ---------------------------------------------------------------------

    /** Установка TCP+TLS-соединения. Больше 15 с ждать бессмысленно — сеть уже не та. */
    private const val CONNECT_TIMEOUT_S = 15L

    /** Пауза между байтами ответа. Ловит именно «залипшее» соединение. */
    private const val READ_TIMEOUT_S = 30L

    /** Пауза между байтами запроса (тела логина/refresh крошечные). */
    private const val WRITE_TIMEOUT_S = 30L

    /** Общий потолок на один вызов API вместе с ретраями и редиректами. */
    private const val CALL_TIMEOUT_S = 60L

    /**
     * read-таймаут для загрузки APK: считается между чтениями, а не на весь файл,
     * поэтому 60 с — это «канал молчит минуту», а не «файл качается минуту».
     * Для медленного мобильного интернета запас нужен больше обычного.
     */
    private const val DOWNLOAD_READ_TIMEOUT_S = 60L

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideApiBaseUrlProvider(): ApiBaseUrlProvider = ApiBaseUrlProvider()

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    // ---------------------------------------------------------------------
    // Сертификатный пиннинг (опциональный, выключен по умолчанию)
    //
    // Особенность архитектуры: хост API НЕ зафиксирован в коде. Пользователь
    // вводит свой домен, discovery отдаёт api_base_url, и он подставляется в
    // рантайме ([ApiBaseUrlProvider] + [InfinityApiInterceptor]). Захардкодить
    // пин под конкретный хост нельзя — пины у разных доменов разные.
    //
    // Выбран ВАРИАНТ 1 — пины из BuildConfig, по умолчанию пустая строка:
    //   * пустая конфигурация ⇒ CertificatePinner вообще не ставится, клиент
    //     ведёт себя ровно как раньше (главный приоритет — не сломать рабочее
    //     приложение у пользователей);
    //   * если при сборке пины заданы, они применяются к хосту из discovery.
    //     Хост становится известен позже, чем создаются клиенты-синглтоны,
    //     поэтому проверка навешивается сетевым интерцептором и выполняется
    //     на каждое соединение (подробности — в applyPinning/buildPinner).
    //
    // Вариант 2 («пины приходят в ответе discovery») отвергнут сознательно:
    // discovery ходит по тому же TLS, что и API, и до успешного discovery
    // доверять нечему. Пин, полученный по непинованному каналу, защищает
    // только от пассивного наблюдателя, но не от активного MITM — то есть
    // ровно от того, ради чего пиннинг и вводится. Это TOFU без закрепления
    // и ложное чувство безопасности.
    //
    // РОТАЦИЯ СЕРТИФИКАТА (важно — при ошибке приложение перестанет работать
    // у ВСЕХ пользователей до выката нового APK):
    //   1. Пин считается от SubjectPublicKeyInfo, а не от самого сертификата,
    //      поэтому перевыпуск сертификата с ТЕМ ЖЕ ключом пин не ломает.
    //   2. В INFINITY_CERT_PINS всегда держать МИНИМУМ ДВА пина: текущий ключ
    //      и запасной (backup) — заранее сгенерированный ключ следующего
    //      выпуска, который лежит офлайн. OkHttp принимает соединение, если
    //      совпал ЛЮБОЙ из пинов, поэтому смена ключа на запасной проходит
    //      без обновления клиента.
    //   3. Порядок замены: выпустить сертификат на backup-ключ → убедиться,
    //      что старые клиенты работают (их backup-пин совпал) → в следующем
    //      релизе клиента заменить отработавший пин на новый backup.
    //   4. Получить пин: `openssl s_client -connect host:443 | openssl x509
    //      -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst
    //      -sha256 -binary | openssl enc -base64`  → "sha256/BASE64".
    //      Пины ставить на промежуточный CA, а не на листовой сертификат,
    //      если сертификат перевыпускается автоматически (Let's Encrypt).
    //   5. АВАРИЙНОЕ ОТКЛЮЧЕНИЕ: убрать значение из buildConfigField
    //      INFINITY_CERT_PINS в app/build.gradle.kts (пустая строка) и
    //      пересобрать — пиннинг просто не применится.
    //
    // Формат BuildConfig.INFINITY_CERT_PINS: пины через запятую, каждый в
    // виде "sha256/BASE64". Пробелы и пустые элементы игнорируются.
    // ---------------------------------------------------------------------

    private fun parsePins(): List<String> =
        BuildConfig.INFINITY_CERT_PINS
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * Строит [CertificatePinner] для хоста, известного только в рантайме.
     *
     * ПОЧЕМУ ПИННЕР ПЕРЕСОБИРАЕТСЯ, А НЕ СОЗДАЁТСЯ ОДИН РАЗ. Хост живёт в
     * [ApiBaseUrlProvider] и заполняется только ПОСЛЕ discovery, а клиенты —
     * синглтоны Hilt: они создаются один раз, причём первый из них создаётся
     * как раз для того, чтобы discovery выполнить. Зафиксируй мы пиннер в
     * момент сборки клиента — хост был бы ещё null, пиннинг не поставился бы
     * никогда, и вся эта ветка оказалась бы мёртвым кодом при включённых
     * пинах. Поэтому пиннер вычисляется на КАЖДОЕ соединение (см.
     * [applyPinning]) и подхватывает хост, как только discovery его узнал.
     *
     * Возвращает `null` при пустом списке пинов либо пока хост неизвестен —
     * тогда соединение идёт без пиннинга (до discovery пинить нечего, а
     * API-запросы без api_base_url всё равно невозможны:
     * [InfinityApiInterceptor] бросает IOException).
     */
    private fun buildPinner(baseUrlProvider: ApiBaseUrlProvider): CertificatePinner? {
        val pins = parsePins()
        if (pins.isEmpty()) return null
        val host = baseUrlProvider.get()?.host ?: return null
        return CertificatePinner.Builder()
            // Пины действуют и на поддомены API-хоста: панель может отдавать
            // ссылки на CDN вида files.<host>, подписанные тем же ключом.
            .add(host, *pins.toTypedArray())
            .add("*.$host", *pins.toTypedArray())
            .build()
    }

    /**
     * Включает пиннинг, если он сконфигурирован; иначе оставляет builder
     * нетронутым (пустые пины ⇒ клиент ровно такой, как до этой правки).
     *
     * Пиннинг вешается интерцептором, а не через `certificatePinner(...)`:
     * тот принимает готовый объект и намертво зашил бы состояние хоста на
     * момент создания синглтон-клиента, то есть ДО discovery (см.
     * [buildPinner]). Интерцептор же вызывается на каждый запрос и проверяет
     * цепочку актуальным пиннером — с тем хостом, который уже известен.
     */
    private fun OkHttpClient.Builder.applyPinning(
        baseUrlProvider: ApiBaseUrlProvider,
    ): OkHttpClient.Builder {
        // Пины заданы на этапе сборки, так что решение «нужен ли пиннинг
        // вообще» принимаем один раз — на каждый запрос парсить нечего.
        if (parsePins().isEmpty()) return this
        return addNetworkInterceptor { chain ->
            val pinner = buildPinner(baseUrlProvider)
            if (pinner != null) {
                val host = chain.request().url.host
                // Сертификаты доступны только у сетевого интерцептора: у
                // интерцептора приложения connection() ещё null.
                val certs = chain.connection()?.handshake()?.peerCertificates.orEmpty()
                // check() сам бросит SSLPeerUnverifiedException при несовпадении
                // и промолчит для хостов, которых нет в списке пинов.
                pinner.check(host, certs)
            }
            chain.proceed(chain.request())
        }
    }

    /**
     * OkHttp для discovery — без Bearer и подмены хоста.
     * Используется также [RawApi] (тела подписок — небольшие текстовые ответы),
     * поэтому общий потолок callTimeout здесь уместен.
     */
    @Provides
    @Singleton
    @Named("discovery")
    fun provideDiscoveryClient(
        logging: HttpLoggingInterceptor,
        baseUrlProvider: ApiBaseUrlProvider,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)
        .applyPinning(baseUrlProvider)
        .addInterceptor(logging)
        .build()

    /** OkHttp для API — Bearer, refresh на 401, динамический хост. */
    @Provides
    @Singleton
    @Named("api")
    fun provideApiClient(
        baseUrlProvider: ApiBaseUrlProvider,
        tokenProvider: TokenProvider,
        logging: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
        // callTimeout считается ВКЛЮЧАЯ повтор запроса после refresh токена
        // (TokenAuthenticator), поэтому 60 с — с запасом на два прохода.
        .callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)
        .applyPinning(baseUrlProvider)
        // Порядок: сначала подмена хоста, затем добавление Bearer.
        .addInterceptor(InfinityApiInterceptor(baseUrlProvider))
        .addInterceptor(AuthInterceptor(tokenProvider))
        .authenticator(TokenAuthenticator(tokenProvider))
        .addInterceptor(logging)
        .build()

    /**
     * OkHttp для скачивания APK обновления (~80 МБ, universal-сборка с двумя
     * нативными ядрами).
     *
     * Отдельный клиент нужен из-за callTimeout: он ограничивает ВЕСЬ вызов,
     * включая чтение тела ответа. На "api"-клиенте (а именно его использовал
     * [ClientUpdateApi] — download идёт абсолютным @Url через тот же граф)
     * любые 60 с оборвали бы загрузку на первом же медленном соединении:
     * 80 МБ за минуту требуют ~11 Мбит/с, чего на мобильной сети обычно нет.
     *
     * Поэтому здесь callTimeout НЕ ставится вовсе, а «залипание» ловится
     * read-таймаутом — он меряет паузу между чтениями из потока, то есть
     * реагирует на молчащий канал, но не наказывает за долгую честную
     * загрузку. Интерсепторы и authenticator те же, что у "api": ссылка на
     * APK ведёт на API-хост, а сам эндпоинт публичный.
     */
    @Provides
    @Singleton
    @Named("download")
    fun provideDownloadClient(
        baseUrlProvider: ApiBaseUrlProvider,
        tokenProvider: TokenProvider,
        logging: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(DOWNLOAD_READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
        // callTimeout намеренно не задан — см. KDoc выше.
        .applyPinning(baseUrlProvider)
        .addInterceptor(InfinityApiInterceptor(baseUrlProvider))
        .addInterceptor(AuthInterceptor(tokenProvider))
        .authenticator(TokenAuthenticator(tokenProvider))
        // Тело APK — бинарь на десятки мегабайт; BODY-логирование в debug
        // держало бы его целиком в памяти, поэтому здесь только заголовки.
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.HEADERS
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            },
        )
        .build()

    @Provides
    @Singleton
    fun provideDiscoveryApi(
        @Named("discovery") client: OkHttpClient,
        json: Json,
    ): DiscoveryApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(DiscoveryApi::class.java)
    }

    @Provides
    @Singleton
    fun provideRawApi(
        @Named("discovery") client: OkHttpClient,
        json: Json,
    ): RawApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(RawApi::class.java)
    }

    @Provides
    @Singleton
    fun provideInfinityApi(
        @Named("api") client: OkHttpClient,
        json: Json,
    ): InfinityApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(InfinityApi::class.java)
    }

    /**
     * API обновлений клиента. Использует "download"-клиент: он такой же, как
     * "api" (динамический хост через InfinityApiInterceptor, эндпоинты
     * публичные — лишний Bearer сервер игнорирует, а до логина токена просто
     * нет), но БЕЗ callTimeout — иначе скачивание ~80-мегабайтного APK
     * обрывалось бы по общему потолку вызова на любом небыстром соединении.
     * Лёгкий `latest()` тоже идёт через него: он защищён connect/read-таймаутами,
     * а отдельный клиент ради одного запроса плодил бы третий пул соединений.
     */
    @Provides
    @Singleton
    fun provideClientUpdateApi(
        @Named("download") client: OkHttpClient,
        json: Json,
    ): ClientUpdateApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ClientUpdateApi::class.java)
    }
}
