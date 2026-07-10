package com.infinityconnect.vpn.domain.usecase;

import com.infinityconnect.vpn.domain.repository.KeysRepository;
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
public final class ObserveKeysUseCase_Factory implements Factory<ObserveKeysUseCase> {
  private final Provider<KeysRepository> keysRepositoryProvider;

  public ObserveKeysUseCase_Factory(Provider<KeysRepository> keysRepositoryProvider) {
    this.keysRepositoryProvider = keysRepositoryProvider;
  }

  @Override
  public ObserveKeysUseCase get() {
    return newInstance(keysRepositoryProvider.get());
  }

  public static ObserveKeysUseCase_Factory create(Provider<KeysRepository> keysRepositoryProvider) {
    return new ObserveKeysUseCase_Factory(keysRepositoryProvider);
  }

  public static ObserveKeysUseCase newInstance(KeysRepository keysRepository) {
    return new ObserveKeysUseCase(keysRepository);
  }
}
