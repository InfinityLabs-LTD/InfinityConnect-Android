package com.infinityconnect.vpn.domain.usecase;

import com.infinityconnect.vpn.domain.repository.AuthRepository;
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
public final class LoginAndSyncUseCase_Factory implements Factory<LoginAndSyncUseCase> {
  private final Provider<AuthRepository> authRepositoryProvider;

  private final Provider<KeysRepository> keysRepositoryProvider;

  public LoginAndSyncUseCase_Factory(Provider<AuthRepository> authRepositoryProvider,
      Provider<KeysRepository> keysRepositoryProvider) {
    this.authRepositoryProvider = authRepositoryProvider;
    this.keysRepositoryProvider = keysRepositoryProvider;
  }

  @Override
  public LoginAndSyncUseCase get() {
    return newInstance(authRepositoryProvider.get(), keysRepositoryProvider.get());
  }

  public static LoginAndSyncUseCase_Factory create(Provider<AuthRepository> authRepositoryProvider,
      Provider<KeysRepository> keysRepositoryProvider) {
    return new LoginAndSyncUseCase_Factory(authRepositoryProvider, keysRepositoryProvider);
  }

  public static LoginAndSyncUseCase newInstance(AuthRepository authRepository,
      KeysRepository keysRepository) {
    return new LoginAndSyncUseCase(authRepository, keysRepository);
  }
}
