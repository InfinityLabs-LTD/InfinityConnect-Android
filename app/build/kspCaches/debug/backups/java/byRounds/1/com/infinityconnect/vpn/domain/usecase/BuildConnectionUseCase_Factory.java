package com.infinityconnect.vpn.domain.usecase;

import com.infinityconnect.vpn.domain.repository.ConfigRepository;
import com.infinityconnect.vpn.domain.repository.KeysRepository;
import com.infinityconnect.vpn.domain.repository.SubscriptionRepository;
import com.infinityconnect.vpn.domain.subscription.SubscriptionParser;
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
public final class BuildConnectionUseCase_Factory implements Factory<BuildConnectionUseCase> {
  private final Provider<KeysRepository> keysRepositoryProvider;

  private final Provider<ConfigRepository> configRepositoryProvider;

  private final Provider<SubscriptionRepository> subscriptionRepositoryProvider;

  private final Provider<SubscriptionParser> parserProvider;

  public BuildConnectionUseCase_Factory(Provider<KeysRepository> keysRepositoryProvider,
      Provider<ConfigRepository> configRepositoryProvider,
      Provider<SubscriptionRepository> subscriptionRepositoryProvider,
      Provider<SubscriptionParser> parserProvider) {
    this.keysRepositoryProvider = keysRepositoryProvider;
    this.configRepositoryProvider = configRepositoryProvider;
    this.subscriptionRepositoryProvider = subscriptionRepositoryProvider;
    this.parserProvider = parserProvider;
  }

  @Override
  public BuildConnectionUseCase get() {
    return newInstance(keysRepositoryProvider.get(), configRepositoryProvider.get(), subscriptionRepositoryProvider.get(), parserProvider.get());
  }

  public static BuildConnectionUseCase_Factory create(
      Provider<KeysRepository> keysRepositoryProvider,
      Provider<ConfigRepository> configRepositoryProvider,
      Provider<SubscriptionRepository> subscriptionRepositoryProvider,
      Provider<SubscriptionParser> parserProvider) {
    return new BuildConnectionUseCase_Factory(keysRepositoryProvider, configRepositoryProvider, subscriptionRepositoryProvider, parserProvider);
  }

  public static BuildConnectionUseCase newInstance(KeysRepository keysRepository,
      ConfigRepository configRepository, SubscriptionRepository subscriptionRepository,
      SubscriptionParser parser) {
    return new BuildConnectionUseCase(keysRepository, configRepository, subscriptionRepository, parser);
  }
}
