package com.infinityconnect.vpn.vpn.hysteria2;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class Hysteria2Engine_Factory implements Factory<Hysteria2Engine> {
  @Override
  public Hysteria2Engine get() {
    return newInstance();
  }

  public static Hysteria2Engine_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static Hysteria2Engine newInstance() {
    return new Hysteria2Engine();
  }

  private static final class InstanceHolder {
    private static final Hysteria2Engine_Factory INSTANCE = new Hysteria2Engine_Factory();
  }
}
