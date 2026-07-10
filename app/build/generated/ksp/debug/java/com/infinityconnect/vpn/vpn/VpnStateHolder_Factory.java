package com.infinityconnect.vpn.vpn;

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
public final class VpnStateHolder_Factory implements Factory<VpnStateHolder> {
  @Override
  public VpnStateHolder get() {
    return newInstance();
  }

  public static VpnStateHolder_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static VpnStateHolder newInstance() {
    return new VpnStateHolder();
  }

  private static final class InstanceHolder {
    private static final VpnStateHolder_Factory INSTANCE = new VpnStateHolder_Factory();
  }
}
