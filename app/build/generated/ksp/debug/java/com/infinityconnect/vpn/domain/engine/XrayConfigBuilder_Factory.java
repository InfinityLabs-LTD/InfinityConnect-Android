package com.infinityconnect.vpn.domain.engine;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.serialization.json.Json;

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
public final class XrayConfigBuilder_Factory implements Factory<XrayConfigBuilder> {
  private final Provider<Json> jsonProvider;

  public XrayConfigBuilder_Factory(Provider<Json> jsonProvider) {
    this.jsonProvider = jsonProvider;
  }

  @Override
  public XrayConfigBuilder get() {
    return newInstance(jsonProvider.get());
  }

  public static XrayConfigBuilder_Factory create(Provider<Json> jsonProvider) {
    return new XrayConfigBuilder_Factory(jsonProvider);
  }

  public static XrayConfigBuilder newInstance(Json json) {
    return new XrayConfigBuilder(json);
  }
}
