package com.infinityconnect.vpn.vpn;

import com.infinityconnect.vpn.vpn.hysteria2.Hysteria2Engine;
import com.infinityconnect.vpn.vpn.xray.XrayEngine;
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
public final class EngineSelector_Factory implements Factory<EngineSelector> {
  private final Provider<XrayEngine> xrayEngineProvider;

  private final Provider<Hysteria2Engine> hysteria2EngineProvider;

  public EngineSelector_Factory(Provider<XrayEngine> xrayEngineProvider,
      Provider<Hysteria2Engine> hysteria2EngineProvider) {
    this.xrayEngineProvider = xrayEngineProvider;
    this.hysteria2EngineProvider = hysteria2EngineProvider;
  }

  @Override
  public EngineSelector get() {
    return newInstance(xrayEngineProvider.get(), hysteria2EngineProvider.get());
  }

  public static EngineSelector_Factory create(Provider<XrayEngine> xrayEngineProvider,
      Provider<Hysteria2Engine> hysteria2EngineProvider) {
    return new EngineSelector_Factory(xrayEngineProvider, hysteria2EngineProvider);
  }

  public static EngineSelector newInstance(XrayEngine xrayEngine, Hysteria2Engine hysteria2Engine) {
    return new EngineSelector(xrayEngine, hysteria2Engine);
  }
}
