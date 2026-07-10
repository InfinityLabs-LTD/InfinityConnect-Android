package com.infinityconnect.vpn.ui.profile;

import com.infinityconnect.vpn.domain.repository.DiscoveryRepository;
import com.infinityconnect.vpn.domain.repository.UserRepository;
import com.infinityconnect.vpn.domain.usecase.LogoutUseCase;
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
public final class ProfileViewModel_Factory implements Factory<ProfileViewModel> {
  private final Provider<UserRepository> userRepositoryProvider;

  private final Provider<LogoutUseCase> logoutUseCaseProvider;

  private final Provider<DiscoveryRepository> discoveryRepositoryProvider;

  public ProfileViewModel_Factory(Provider<UserRepository> userRepositoryProvider,
      Provider<LogoutUseCase> logoutUseCaseProvider,
      Provider<DiscoveryRepository> discoveryRepositoryProvider) {
    this.userRepositoryProvider = userRepositoryProvider;
    this.logoutUseCaseProvider = logoutUseCaseProvider;
    this.discoveryRepositoryProvider = discoveryRepositoryProvider;
  }

  @Override
  public ProfileViewModel get() {
    return newInstance(userRepositoryProvider.get(), logoutUseCaseProvider.get(), discoveryRepositoryProvider.get());
  }

  public static ProfileViewModel_Factory create(Provider<UserRepository> userRepositoryProvider,
      Provider<LogoutUseCase> logoutUseCaseProvider,
      Provider<DiscoveryRepository> discoveryRepositoryProvider) {
    return new ProfileViewModel_Factory(userRepositoryProvider, logoutUseCaseProvider, discoveryRepositoryProvider);
  }

  public static ProfileViewModel newInstance(UserRepository userRepository,
      LogoutUseCase logoutUseCase, DiscoveryRepository discoveryRepository) {
    return new ProfileViewModel(userRepository, logoutUseCase, discoveryRepository);
  }
}
