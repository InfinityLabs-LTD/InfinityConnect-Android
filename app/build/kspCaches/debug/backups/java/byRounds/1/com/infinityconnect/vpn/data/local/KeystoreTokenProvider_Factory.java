package com.infinityconnect.vpn.data.local;

import com.infinityconnect.vpn.data.remote.ApiBaseUrlProvider;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.serialization.json.Json;
import okhttp3.OkHttpClient;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("javax.inject.Named")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class KeystoreTokenProvider_Factory implements Factory<KeystoreTokenProvider> {
  private final Provider<TokenStorage> storageProvider;

  private final Provider<ApiBaseUrlProvider> baseUrlProvider;

  private final Provider<OkHttpClient> refreshClientProvider;

  private final Provider<Json> jsonProvider;

  private final Provider<SessionState> sessionStateProvider;

  public KeystoreTokenProvider_Factory(Provider<TokenStorage> storageProvider,
      Provider<ApiBaseUrlProvider> baseUrlProvider, Provider<OkHttpClient> refreshClientProvider,
      Provider<Json> jsonProvider, Provider<SessionState> sessionStateProvider) {
    this.storageProvider = storageProvider;
    this.baseUrlProvider = baseUrlProvider;
    this.refreshClientProvider = refreshClientProvider;
    this.jsonProvider = jsonProvider;
    this.sessionStateProvider = sessionStateProvider;
  }

  @Override
  public KeystoreTokenProvider get() {
    return newInstance(storageProvider.get(), baseUrlProvider.get(), refreshClientProvider.get(), jsonProvider.get(), sessionStateProvider.get());
  }

  public static KeystoreTokenProvider_Factory create(Provider<TokenStorage> storageProvider,
      Provider<ApiBaseUrlProvider> baseUrlProvider, Provider<OkHttpClient> refreshClientProvider,
      Provider<Json> jsonProvider, Provider<SessionState> sessionStateProvider) {
    return new KeystoreTokenProvider_Factory(storageProvider, baseUrlProvider, refreshClientProvider, jsonProvider, sessionStateProvider);
  }

  public static KeystoreTokenProvider newInstance(TokenStorage storage,
      ApiBaseUrlProvider baseUrlProvider, OkHttpClient refreshClient, Json json,
      SessionState sessionState) {
    return new KeystoreTokenProvider(storage, baseUrlProvider, refreshClient, json, sessionState);
  }
}
