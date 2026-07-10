package com.infinityconnect.vpn.data.repository;

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
public final class KeysRepositoryImpl_Factory implements Factory<KeysRepositoryImpl> {
  private final Provider<InfinityApi> apiProvider;

  public KeysRepositoryImpl_Factory(Provider<InfinityApi> apiProvider) {
    this.apiProvider = apiProvider;
  }

  @Override
  public KeysRepositoryImpl get() {
    return newInstance(apiProvider.get());
  }

  public static KeysRepositoryImpl_Factory create(Provider<InfinityApi> apiProvider) {
    return new KeysRepositoryImpl_Factory(apiProvider);
  }

  public static KeysRepositoryImpl newInstance(InfinityApi api) {
    return new KeysRepositoryImpl(api);
  }
}
