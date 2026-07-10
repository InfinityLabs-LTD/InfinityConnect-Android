package com.infinityconnect.vpn.di

import com.infinityconnect.vpn.BuildConfig
import com.infinityconnect.vpn.data.remote.ApiBaseUrlProvider
import com.infinityconnect.vpn.data.remote.AuthInterceptor
import com.infinityconnect.vpn.data.remote.InfinityApiInterceptor
import com.infinityconnect.vpn.data.remote.TokenAuthenticator
import com.infinityconnect.vpn.data.remote.TokenProvider
import com.infinityconnect.vpn.data.remote.api.DiscoveryApi
import com.infinityconnect.vpn.data.remote.api.InfinityApi
import com.infinityconnect.vpn.data.remote.api.RawApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt-модуль сетевого слоя. Собирает два Retrofit:
 *  - [DiscoveryApi] — для абсолютного @Url-запроса /v1/discovery (без Bearer);
 *  - [InfinityApi]  — для относительных запросов API с авто-Bearer, refresh и
 *    динамическим api_base_url ([InfinityApiInterceptor]).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Плейсхолдерный базовый URL: реальный хост подставляет InfinityApiInterceptor.
    private const val PLACEHOLDER_BASE = "https://placeholder.invalid/"

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

    /** OkHttp для discovery — без Bearer и подмены хоста. */
    @Provides
    @Singleton
    @Named("discovery")
    fun provideDiscoveryClient(
        logging: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
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
        // Порядок: сначала подмена хоста, затем добавление Bearer.
        .addInterceptor(InfinityApiInterceptor(baseUrlProvider))
        .addInterceptor(AuthInterceptor(tokenProvider))
        .authenticator(TokenAuthenticator(tokenProvider))
        .addInterceptor(logging)
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
}
