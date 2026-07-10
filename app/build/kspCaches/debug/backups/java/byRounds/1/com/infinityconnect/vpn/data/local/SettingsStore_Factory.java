package com.infinityconnect.vpn.data.local;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.serialization.json.Json;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class SettingsStore_Factory implements Factory<SettingsStore> {
  private final Provider<Context> contextProvider;

  private final Provider<Json> jsonProvider;

  public SettingsStore_Factory(Provider<Context> contextProvider, Provider<Json> jsonProvider) {
    this.contextProvider = contextProvider;
    this.jsonProvider = jsonProvider;
  }

  @Override
  public SettingsStore get() {
    return newInstance(contextProvider.get(), jsonProvider.get());
  }

  public static SettingsStore_Factory create(Provider<Context> contextProvider,
      Provider<Json> jsonProvider) {
    return new SettingsStore_Factory(contextProvider, jsonProvider);
  }

  public static SettingsStore newInstance(Context context, Json json) {
    return new SettingsStore(context, json);
  }
}
