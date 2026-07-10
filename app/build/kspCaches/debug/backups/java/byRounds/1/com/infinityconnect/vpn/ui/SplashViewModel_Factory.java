package com.infinityconnect.vpn.ui;

import com.infinityconnect.vpn.domain.repository.AuthRepository;
import com.infinityconnect.vpn.domain.repository.DiscoveryRepository;
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
public final class SplashViewModel_Factory implements Factory<SplashViewModel> {
  private final Provider<DiscoveryRepository> discoveryRepositoryProvider;

  private final Provider<AuthRepository> authRepositoryProvider;

  public SplashViewModel_Factory(Provider<DiscoveryRepository> discoveryRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider) {
    this.discoveryRepositoryProvider = discoveryRepositoryProvider;
    this.authRepositoryProvider = authRepositoryProvider;
  }

  @Override
  public SplashViewModel get() {
    return newInstance(discoveryRepositoryProvider.get(), authRepositoryProvider.get());
  }

  public static SplashViewModel_Factory create(
      Provider<DiscoveryRepository> discoveryRepositoryProvider,
      Provider<AuthRepository> authRepositoryProvider) {
    return new SplashViewModel_Factory(discoveryRepositoryProvider, authRepositoryProvider);
  }

  public static SplashViewModel newInstance(DiscoveryRepository discoveryRepository,
      AuthRepository authRepository) {
    return new SplashViewModel(discoveryRepository, authRepository);
  }
}
