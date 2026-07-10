package com.infinityconnect.vpn.vpn;

import com.infinityconnect.vpn.domain.usecase.BuildConnectionUseCase;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class InfinityVpnService_MembersInjector implements MembersInjector<InfinityVpnService> {
  private final Provider<BuildConnectionUseCase> buildConnectionProvider;

  private final Provider<EngineSelector> engineSelectorProvider;

  private final Provider<VpnStateHolder> stateHolderProvider;

  public InfinityVpnService_MembersInjector(
      Provider<BuildConnectionUseCase> buildConnectionProvider,
      Provider<EngineSelector> engineSelectorProvider,
      Provider<VpnStateHolder> stateHolderProvider) {
    this.buildConnectionProvider = buildConnectionProvider;
    this.engineSelectorProvider = engineSelectorProvider;
    this.stateHolderProvider = stateHolderProvider;
  }

  public static MembersInjector<InfinityVpnService> create(
      Provider<BuildConnectionUseCase> buildConnectionProvider,
      Provider<EngineSelector> engineSelectorProvider,
      Provider<VpnStateHolder> stateHolderProvider) {
    return new InfinityVpnService_MembersInjector(buildConnectionProvider, engineSelectorProvider, stateHolderProvider);
  }

  @Override
  public void injectMembers(InfinityVpnService instance) {
    injectBuildConnection(instance, buildConnectionProvider.get());
    injectEngineSelector(instance, engineSelectorProvider.get());
    injectStateHolder(instance, stateHolderProvider.get());
  }

  @InjectedFieldSignature("com.infinityconnect.vpn.vpn.InfinityVpnService.buildConnection")
  public static void injectBuildConnection(InfinityVpnService instance,
      BuildConnectionUseCase buildConnection) {
    instance.buildConnection = buildConnection;
  }

  @InjectedFieldSignature("com.infinityconnect.vpn.vpn.InfinityVpnService.engineSelector")
  public static void injectEngineSelector(InfinityVpnService instance,
      EngineSelector engineSelector) {
    instance.engineSelector = engineSelector;
  }

  @InjectedFieldSignature("com.infinityconnect.vpn.vpn.InfinityVpnService.stateHolder")
  public static void injectStateHolder(InfinityVpnService instance, VpnStateHolder stateHolder) {
    instance.stateHolder = stateHolder;
  }
}
