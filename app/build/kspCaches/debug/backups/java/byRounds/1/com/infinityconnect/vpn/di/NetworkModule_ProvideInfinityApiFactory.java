package com.infinityconnect.vpn.di;

import com.infinityconnect.vpn.data.remote.api.InfinityApi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.serialization.json.Json;
import okhttp3.OkHttpClient;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("javax.inject.Named")
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
public final class NetworkModule_ProvideInfinityApiFactory implements Factory<InfinityApi> {
  private final Provider<OkHttpClient> clientProvider;

  private final Provider<Json> jsonProvider;

  public NetworkModule_ProvideInfinityApiFactory(Provider<OkHttpClient> clientProvider,
      Provider<Json> jsonProvider) {
    this.clientProvider = clientProvider;
    this.jsonProvider = jsonProvider;
  }

  @Override
  public InfinityApi get() {
    return provideInfinityApi(clientProvider.get(), jsonProvider.get());
  }

  public static NetworkModule_ProvideInfinityApiFactory create(
      Provider<OkHttpClient> clientProvider, Provider<Json> jsonProvider) {
    return new NetworkModule_ProvideInfinityApiFactory(clientProvider, jsonProvider);
  }

  public static InfinityApi provideInfinityApi(OkHttpClient client, Json json) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideInfinityApi(client, json));
  }
}
