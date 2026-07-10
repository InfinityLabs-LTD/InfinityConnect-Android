package com.infinityconnect.vpn.ui.home;

import com.infinityconnect.vpn.domain.usecase.ObserveKeysUseCase;
import com.infinityconnect.vpn.domain.usecase.SyncKeysUseCase;
import com.infinityconnect.vpn.vpn.VpnController;
import com.infinityconnect.vpn.vpn.VpnStateHolder;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class HomeViewModel_Factory implements Factory<HomeViewModel> {
  private final Provider<ObserveKeysUseCase> observeKeysProvider;

  private final Provider<SyncKeysUseCase> syncKeysProvider;

  private final Provider<VpnController> vpnControllerProvider;

  private final Provider<VpnStateHolder> stateHolderProvider;

  public HomeViewModel_Factory(Provider<ObserveKeysUseCase> observeKeysProvider,
      Provider<SyncKeysUseCase> syncKeysProvider, Provider<VpnController> vpnControllerProvider,
      Provider<VpnStateHolder> stateHolderProvider) {
    this.observeKeysProvider = observeKeysProvider;
    this.syncKeysProvider = syncKeysProvider;
    this.vpnControllerProvider = vpnControllerProvider;
    this.stateHolderProvider = stateHolderProvider;
  }

  @Override
  public HomeViewModel get() {
    return newInstance(observeKeysProvider.get(), syncKeysProvider.get(), vpnControllerProvider.get(), stateHolderProvider.get());
  }

  public static HomeViewModel_Factory create(Provider<ObserveKeysUseCase> observeKeysProvider,
      Provider<SyncKeysUseCase> syncKeysProvider, Provider<VpnController> vpnControllerProvider,
      Provider<VpnStateHolder> stateHolderProvider) {
    return new HomeViewModel_Factory(observeKeysProvider, syncKeysProvider, vpnControllerProvider, stateHolderProvider);
  }

  public static HomeViewModel newInstance(ObserveKeysUseCase observeKeys, SyncKeysUseCase syncKeys,
      VpnController vpnController, VpnStateHolder stateHolder) {
    return new HomeViewModel(observeKeys, syncKeys, vpnController, stateHolder);
  }
}
