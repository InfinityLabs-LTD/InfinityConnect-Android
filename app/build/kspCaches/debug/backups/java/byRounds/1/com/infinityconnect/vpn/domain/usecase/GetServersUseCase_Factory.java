package com.infinityconnect.vpn.domain.usecase;

import com.infinityconnect.vpn.domain.repository.ConfigRepository;
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
public final class GetServersUseCase_Factory implements Factory<GetServersUseCase> {
  private final Provider<ConfigRepository> configRepositoryProvider;

  public GetServersUseCase_Factory(Provider<ConfigRepository> configRepositoryProvider) {
    this.configRepositoryProvider = configRepositoryProvider;
  }

  @Override
  public GetServersUseCase get() {
    return newInstance(configRepositoryProvider.get());
  }

  public static GetServersUseCase_Factory create(
      Provider<ConfigRepository> configRepositoryProvider) {
    return new GetServersUseCase_Factory(configRepositoryProvider);
  }

  public static GetServersUseCase newInstance(ConfigRepository configRepository) {
    return new GetServersUseCase(configRepository);
  }
}
