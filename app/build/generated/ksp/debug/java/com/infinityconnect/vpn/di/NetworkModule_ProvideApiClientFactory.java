package com.infinityconnect.vpn.di;

import com.infinityconnect.vpn.data.remote.ApiBaseUrlProvider;
import com.infinityconnect.vpn.data.remote.TokenProvider;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

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
public final class NetworkModule_ProvideApiClientFactory implements Factory<OkHttpClient> {
  private final Provider<ApiBaseUrlProvider> baseUrlProvider;

  private final Provider<TokenProvider> tokenProvider;

  private final Provider<HttpLoggingInterceptor> loggingProvider;

  public NetworkModule_ProvideApiClientFactory(Provider<ApiBaseUrlProvider> baseUrlProvider,
      Provider<TokenProvider> tokenProvider, Provider<HttpLoggingInterceptor> loggingProvider) {
    this.baseUrlProvider = baseUrlProvider;
    this.tokenProvider = tokenProvider;
    this.loggingProvider = loggingProvider;
  }

  @Override
  public OkHttpClient get() {
    return provideApiClient(baseUrlProvider.get(), tokenProvider.get(), loggingProvider.get());
  }

  public static NetworkModule_ProvideApiClientFactory create(
      Provider<ApiBaseUrlProvider> baseUrlProvider, Provider<TokenProvider> tokenProvider,
      Provider<HttpLoggingInterceptor> loggingProvider) {
    return new NetworkModule_ProvideApiClientFactory(baseUrlProvider, tokenProvider, loggingProvider);
  }

  public static OkHttpClient provideApiClient(ApiBaseUrlProvider baseUrlProvider,
      TokenProvider tokenProvider, HttpLoggingInterceptor logging) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideApiClient(baseUrlProvider, tokenProvider, logging));
  }
}
