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
public final class ConfigRepositoryImpl_Factory implements Factory<ConfigRepositoryImpl> {
  private final Provider<InfinityApi> apiProvider;

  public ConfigRepositoryImpl_Factory(Provider<InfinityApi> apiProvider) {
    this.apiProvider = apiProvider;
  }

  @Override
  public ConfigRepositoryImpl get() {
    return newInstance(apiProvider.get());
  }

  public static ConfigRepositoryImpl_Factory create(Provider<InfinityApi> apiProvider) {
    return new ConfigRepositoryImpl_Factory(apiProvider);
  }

  public static ConfigRepositoryImpl newInstance(InfinityApi api) {
    return new ConfigRepositoryImpl(api);
  }
}
