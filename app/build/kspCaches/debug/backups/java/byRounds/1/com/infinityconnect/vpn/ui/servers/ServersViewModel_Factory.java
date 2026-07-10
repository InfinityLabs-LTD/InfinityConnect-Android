package com.infinityconnect.vpn.ui.servers;

import androidx.lifecycle.SavedStateHandle;
import com.infinityconnect.vpn.domain.usecase.GetServersUseCase;
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
public final class ServersViewModel_Factory implements Factory<ServersViewModel> {
  private final Provider<GetServersUseCase> getServersProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  public ServersViewModel_Factory(Provider<GetServersUseCase> getServersProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.getServersProvider = getServersProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public ServersViewModel get() {
    return newInstance(getServersProvider.get(), savedStateHandleProvider.get());
  }

  public static ServersViewModel_Factory create(Provider<GetServersUseCase> getServersProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new ServersViewModel_Factory(getServersProvider, savedStateHandleProvider);
  }

  public static ServersViewModel newInstance(GetServersUseCase getServers,
      SavedStateHandle savedStateHandle) {
    return new ServersViewModel(getServers, savedStateHandle);
  }
}
