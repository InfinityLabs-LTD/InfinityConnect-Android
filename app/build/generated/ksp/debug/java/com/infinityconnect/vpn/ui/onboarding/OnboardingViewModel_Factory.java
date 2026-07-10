package com.infinityconnect.vpn.ui.onboarding;

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
public final class OnboardingViewModel_Factory implements Factory<OnboardingViewModel> {
  private final Provider<DiscoveryRepository> discoveryRepositoryProvider;

  public OnboardingViewModel_Factory(Provider<DiscoveryRepository> discoveryRepositoryProvider) {
    this.discoveryRepositoryProvider = discoveryRepositoryProvider;
  }

  @Override
  public OnboardingViewModel get() {
    return newInstance(discoveryRepositoryProvider.get());
  }

  public static OnboardingViewModel_Factory create(
      Provider<DiscoveryRepository> discoveryRepositoryProvider) {
    return new OnboardingViewModel_Factory(discoveryRepositoryProvider);
  }

  public static OnboardingViewModel newInstance(DiscoveryRepository discoveryRepository) {
    return new OnboardingViewModel(discoveryRepository);
  }
}
