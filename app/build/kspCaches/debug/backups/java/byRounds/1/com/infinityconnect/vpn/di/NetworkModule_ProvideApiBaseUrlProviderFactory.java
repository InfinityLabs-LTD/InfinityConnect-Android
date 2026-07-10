package com.infinityconnect.vpn.di;

import com.infinityconnect.vpn.data.remote.ApiBaseUrlProvider;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class NetworkModule_ProvideApiBaseUrlProviderFactory implements Factory<ApiBaseUrlProvider> {
  @Override
  public ApiBaseUrlProvider get() {
    return provideApiBaseUrlProvider();
  }

  public static NetworkModule_ProvideApiBaseUrlProviderFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ApiBaseUrlProvider provideApiBaseUrlProvider() {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideApiBaseUrlProvider());
  }

  private static final class InstanceHolder {
    private static final NetworkModule_ProvideApiBaseUrlProviderFactory INSTANCE = new NetworkModule_ProvideApiBaseUrlProviderFactory();
  }
}
