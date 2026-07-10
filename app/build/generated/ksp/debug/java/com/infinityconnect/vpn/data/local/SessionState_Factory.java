package com.infinityconnect.vpn.data.local;

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
public final class SessionState_Factory implements Factory<SessionState> {
  private final Provider<TokenStorage> tokenStorageProvider;

  public SessionState_Factory(Provider<TokenStorage> tokenStorageProvider) {
    this.tokenStorageProvider = tokenStorageProvider;
  }

  @Override
  public SessionState get() {
    return newInstance(tokenStorageProvider.get());
  }

  public static SessionState_Factory create(Provider<TokenStorage> tokenStorageProvider) {
    return new SessionState_Factory(tokenStorageProvider);
  }

  public static SessionState newInstance(TokenStorage tokenStorage) {
    return new SessionState(tokenStorage);
  }
}
