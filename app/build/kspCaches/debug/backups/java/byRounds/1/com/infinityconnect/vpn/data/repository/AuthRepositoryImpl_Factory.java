package com.infinityconnect.vpn.data.repository;

import com.infinityconnect.vpn.data.local.SessionState;
import com.infinityconnect.vpn.data.local.TokenStorage;
import com.infinityconnect.vpn.data.remote.api.InfinityApi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
public final class AuthRepositoryImpl_Factory implements Factory<AuthRepositoryImpl> {
  private final Provider<InfinityApi> apiProvider;

  private final Provider<TokenStorage> storageProvider;

  private final Provider<SessionState> sessionStateProvider;

  public AuthRepositoryImpl_Factory(Provider<InfinityApi> apiProvider,
      Provider<TokenStorage> storageProvider, Provider<SessionState> sessionStateProvider) {
    this.apiProvider = apiProvider;
    this.storageProvider = storageProvider;
    this.sessionStateProvider = sessionStateProvider;
  }

  @Override
  public AuthRepositoryImpl get() {
    return newInstance(apiProvider.get(), storageProvider.get(), sessionStateProvider.get());
  }

  public static AuthRepositoryImpl_Factory create(Provider<InfinityApi> apiProvider,
      Provider<TokenStorage> storageProvider, Provider<SessionState> sessionStateProvider) {
    return new AuthRepositoryImpl_Factory(apiProvider, storageProvider, sessionStateProvider);
  }

  public static AuthRepositoryImpl newInstance(InfinityApi api, TokenStorage storage,
      SessionState sessionState) {
    return new AuthRepositoryImpl(api, storage, sessionState);
  }
}
