package com.infinityconnect.vpn.domain.subscription;

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
public final class SubscriptionParser_Factory implements Factory<SubscriptionParser> {
  @Override
  public SubscriptionParser get() {
    return newInstance();
  }

  public static SubscriptionParser_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SubscriptionParser newInstance() {
    return new SubscriptionParser();
  }

  private static final class InstanceHolder {
    private static final SubscriptionParser_Factory INSTANCE = new SubscriptionParser_Factory();
  }
}
