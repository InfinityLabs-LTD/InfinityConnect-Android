package com.infinityconnect.vpn.data.repository;

import com.infinityconnect.vpn.data.local.SettingsStore;
import com.infinityconnect.vpn.data.remote.ApiBaseUrlProvider;
import com.infinityconnect.vpn.data.remote.api.DiscoveryApi;
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
public final class DiscoveryRepositoryImpl_Factory implements Factory<DiscoveryRepositoryImpl> {
  private final Provider<DiscoveryApi> discoveryApiProvider;

  private final Provider<SettingsStore> settingsProvider;

  private final Provider<ApiBaseUrlProvider> baseUrlProvider;

  public DiscoveryRepositoryImpl_Factory(Provider<DiscoveryApi> discoveryApiProvider,
      Provider<SettingsStore> settingsProvider, Provider<ApiBaseUrlProvider> baseUrlProvider) {
    this.discoveryApiProvider = discoveryApiProvider;
    this.settingsProvider = settingsProvider;
    this.baseUrlProvider = baseUrlProvider;
  }

  @Override
  public DiscoveryRepositoryImpl get() {
    return newInstance(discoveryApiProvider.get(), settingsProvider.get(), baseUrlProvider.get());
  }

  public static DiscoveryRepositoryImpl_Factory create(Provider<DiscoveryApi> discoveryApiProvider,
      Provider<SettingsStore> settingsProvider, Provider<ApiBaseUrlProvider> baseUrlProvider) {
    return new DiscoveryRepositoryImpl_Factory(discoveryApiProvider, settingsProvider, baseUrlProvider);
  }

  public static DiscoveryRepositoryImpl newInstance(DiscoveryApi discoveryApi,
      SettingsStore settings, ApiBaseUrlProvider baseUrlProvider) {
    return new DiscoveryRepositoryImpl(discoveryApi, settings, baseUrlProvider);
  }
}
