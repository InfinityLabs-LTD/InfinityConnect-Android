package com.infinityconnect.vpn.data.repository;

import com.infinityconnect.vpn.data.remote.api.RawApi;
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
public final class SubscriptionRepositoryImpl_Factory implements Factory<SubscriptionRepositoryImpl> {
  private final Provider<RawApi> rawApiProvider;

  public SubscriptionRepositoryImpl_Factory(Provider<RawApi> rawApiProvider) {
    this.rawApiProvider = rawApiProvider;
  }

  @Override
  public SubscriptionRepositoryImpl get() {
    return newInstance(rawApiProvider.get());
  }

  public static SubscriptionRepositoryImpl_Factory create(Provider<RawApi> rawApiProvider) {
    return new SubscriptionRepositoryImpl_Factory(rawApiProvider);
  }

  public static SubscriptionRepositoryImpl newInstance(RawApi rawApi) {
    return new SubscriptionRepositoryImpl(rawApi);
  }
}
