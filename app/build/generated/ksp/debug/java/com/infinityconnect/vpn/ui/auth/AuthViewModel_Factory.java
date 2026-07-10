package com.infinityconnect.vpn.ui.auth;

import com.infinityconnect.vpn.domain.repository.DiscoveryRepository;
import com.infinityconnect.vpn.domain.usecase.LoginAndSyncUseCase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class AuthViewModel_Factory implements Factory<AuthViewModel> {
  private final Provider<LoginAndSyncUseCase> loginAndSyncProvider;

  private final Provider<DiscoveryRepository> discoveryRepositoryProvider;

  public AuthViewModel_Factory(Provider<LoginAndSyncUseCase> loginAndSyncProvider,
      Provider<DiscoveryRepository> discoveryRepositoryProvider) {
    this.loginAndSyncProvider = loginAndSyncProvider;
    this.discoveryRepositoryProvider = discoveryRepositoryProvider;
  }

  @Override
  public AuthViewModel get() {
    return newInstance(loginAndSyncProvider.get(), discoveryRepositoryProvider.get());
  }

  public static AuthViewModel_Factory create(Provider<LoginAndSyncUseCase> loginAndSyncProvider,
      Provider<DiscoveryRepository> discoveryRepositoryProvider) {
    return new AuthViewModel_Factory(loginAndSyncProvider, discoveryRepositoryProvider);
  }

  public static AuthViewModel newInstance(LoginAndSyncUseCase loginAndSync,
      DiscoveryRepository discoveryRepository) {
    return new AuthViewModel(loginAndSync, discoveryRepository);
  }
}
