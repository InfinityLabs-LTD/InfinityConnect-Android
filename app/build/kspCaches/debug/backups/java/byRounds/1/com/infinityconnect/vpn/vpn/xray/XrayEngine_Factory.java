package com.infinityconnect.vpn.vpn.xray;

import android.content.Context;
import com.infinityconnect.vpn.domain.engine.XrayConfigBuilder;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class XrayEngine_Factory implements Factory<XrayEngine> {
  private final Provider<Context> contextProvider;

  private final Provider<XrayConfigBuilder> configBuilderProvider;

  public XrayEngine_Factory(Provider<Context> contextProvider,
      Provider<XrayConfigBuilder> configBuilderProvider) {
    this.contextProvider = contextProvider;
    this.configBuilderProvider = configBuilderProvider;
  }

  @Override
  public XrayEngine get() {
    return newInstance(contextProvider.get(), configBuilderProvider.get());
  }

  public static XrayEngine_Factory create(Provider<Context> contextProvider,
      Provider<XrayConfigBuilder> configBuilderProvider) {
    return new XrayEngine_Factory(contextProvider, configBuilderProvider);
  }

  public static XrayEngine newInstance(Context context, XrayConfigBuilder configBuilder) {
    return new XrayEngine(context, configBuilder);
  }
}
