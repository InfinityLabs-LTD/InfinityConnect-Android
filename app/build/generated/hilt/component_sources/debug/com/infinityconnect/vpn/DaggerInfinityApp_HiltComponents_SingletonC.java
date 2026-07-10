package com.infinityconnect.vpn;

import android.app.Activity;
import android.app.Service;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.infinityconnect.vpn.data.local.KeystoreTokenProvider;
import com.infinityconnect.vpn.data.local.SessionState;
import com.infinityconnect.vpn.data.local.SettingsStore;
import com.infinityconnect.vpn.data.local.TokenStorage;
import com.infinityconnect.vpn.data.remote.ApiBaseUrlProvider;
import com.infinityconnect.vpn.data.remote.api.DiscoveryApi;
import com.infinityconnect.vpn.data.remote.api.InfinityApi;
import com.infinityconnect.vpn.data.remote.api.RawApi;
import com.infinityconnect.vpn.data.repository.AuthRepositoryImpl;
import com.infinityconnect.vpn.data.repository.ConfigRepositoryImpl;
import com.infinityconnect.vpn.data.repository.DiscoveryRepositoryImpl;
import com.infinityconnect.vpn.data.repository.KeysRepositoryImpl;
import com.infinityconnect.vpn.data.repository.SubscriptionRepositoryImpl;
import com.infinityconnect.vpn.data.repository.UserRepositoryImpl;
import com.infinityconnect.vpn.di.NetworkModule_ProvideApiBaseUrlProviderFactory;
import com.infinityconnect.vpn.di.NetworkModule_ProvideApiClientFactory;
import com.infinityconnect.vpn.di.NetworkModule_ProvideDiscoveryApiFactory;
import com.infinityconnect.vpn.di.NetworkModule_ProvideDiscoveryClientFactory;
import com.infinityconnect.vpn.di.NetworkModule_ProvideInfinityApiFactory;
import com.infinityconnect.vpn.di.NetworkModule_ProvideJsonFactory;
import com.infinityconnect.vpn.di.NetworkModule_ProvideLoggingInterceptorFactory;
import com.infinityconnect.vpn.di.NetworkModule_ProvideRawApiFactory;
import com.infinityconnect.vpn.domain.engine.XrayConfigBuilder;
import com.infinityconnect.vpn.domain.subscription.SubscriptionParser;
import com.infinityconnect.vpn.domain.usecase.BuildConnectionUseCase;
import com.infinityconnect.vpn.domain.usecase.GetServersUseCase;
import com.infinityconnect.vpn.domain.usecase.LoginAndSyncUseCase;
import com.infinityconnect.vpn.domain.usecase.LogoutUseCase;
import com.infinityconnect.vpn.domain.usecase.ObserveKeysUseCase;
import com.infinityconnect.vpn.domain.usecase.SyncKeysUseCase;
import com.infinityconnect.vpn.ui.MainActivity;
import com.infinityconnect.vpn.ui.SplashViewModel;
import com.infinityconnect.vpn.ui.SplashViewModel_HiltModules;
import com.infinityconnect.vpn.ui.SplashViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.infinityconnect.vpn.ui.SplashViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.infinityconnect.vpn.ui.auth.AuthViewModel;
import com.infinityconnect.vpn.ui.auth.AuthViewModel_HiltModules;
import com.infinityconnect.vpn.ui.auth.AuthViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.infinityconnect.vpn.ui.auth.AuthViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.infinityconnect.vpn.ui.home.HomeViewModel;
import com.infinityconnect.vpn.ui.home.HomeViewModel_HiltModules;
import com.infinityconnect.vpn.ui.home.HomeViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.infinityconnect.vpn.ui.home.HomeViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.infinityconnect.vpn.ui.onboarding.OnboardingViewModel;
import com.infinityconnect.vpn.ui.onboarding.OnboardingViewModel_HiltModules;
import com.infinityconnect.vpn.ui.onboarding.OnboardingViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.infinityconnect.vpn.ui.onboarding.OnboardingViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.infinityconnect.vpn.ui.profile.ProfileViewModel;
import com.infinityconnect.vpn.ui.profile.ProfileViewModel_HiltModules;
import com.infinityconnect.vpn.ui.profile.ProfileViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.infinityconnect.vpn.ui.profile.ProfileViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.infinityconnect.vpn.ui.servers.ServersViewModel;
import com.infinityconnect.vpn.ui.servers.ServersViewModel_HiltModules;
import com.infinityconnect.vpn.ui.servers.ServersViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.infinityconnect.vpn.ui.servers.ServersViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.infinityconnect.vpn.vpn.EngineSelector;
import com.infinityconnect.vpn.vpn.InfinityVpnService;
import com.infinityconnect.vpn.vpn.InfinityVpnService_MembersInjector;
import com.infinityconnect.vpn.vpn.VpnController;
import com.infinityconnect.vpn.vpn.VpnStateHolder;
import com.infinityconnect.vpn.vpn.hysteria2.Hysteria2Engine;
import com.infinityconnect.vpn.vpn.xray.XrayEngine;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.MapBuilder;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;
import kotlinx.serialization.json.Json;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

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
public final class DaggerInfinityApp_HiltComponents_SingletonC {
  private DaggerInfinityApp_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public InfinityApp_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements InfinityApp_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public InfinityApp_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements InfinityApp_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public InfinityApp_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements InfinityApp_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public InfinityApp_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements InfinityApp_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public InfinityApp_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements InfinityApp_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public InfinityApp_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements InfinityApp_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public InfinityApp_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements InfinityApp_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public InfinityApp_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends InfinityApp_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    private ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends InfinityApp_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    private FragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends InfinityApp_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    private ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends InfinityApp_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    private ActivityCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity arg0) {
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(MapBuilder.<String, Boolean>newMapBuilder(6).put(AuthViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, AuthViewModel_HiltModules.KeyModule.provide()).put(HomeViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, HomeViewModel_HiltModules.KeyModule.provide()).put(OnboardingViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, OnboardingViewModel_HiltModules.KeyModule.provide()).put(ProfileViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ProfileViewModel_HiltModules.KeyModule.provide()).put(ServersViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ServersViewModel_HiltModules.KeyModule.provide()).put(SplashViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SplashViewModel_HiltModules.KeyModule.provide()).build());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }
  }

  private static final class ViewModelCImpl extends InfinityApp_HiltComponents.ViewModelC {
    private final SavedStateHandle savedStateHandle;

    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    private Provider<AuthViewModel> authViewModelProvider;

    private Provider<HomeViewModel> homeViewModelProvider;

    private Provider<OnboardingViewModel> onboardingViewModelProvider;

    private Provider<ProfileViewModel> profileViewModelProvider;

    private Provider<ServersViewModel> serversViewModelProvider;

    private Provider<SplashViewModel> splashViewModelProvider;

    private ViewModelCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, SavedStateHandle savedStateHandleParam,
        ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.savedStateHandle = savedStateHandleParam;
      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    private LoginAndSyncUseCase loginAndSyncUseCase() {
      return new LoginAndSyncUseCase(singletonCImpl.authRepositoryImplProvider.get(), singletonCImpl.keysRepositoryImplProvider.get());
    }

    private ObserveKeysUseCase observeKeysUseCase() {
      return new ObserveKeysUseCase(singletonCImpl.keysRepositoryImplProvider.get());
    }

    private SyncKeysUseCase syncKeysUseCase() {
      return new SyncKeysUseCase(singletonCImpl.keysRepositoryImplProvider.get());
    }

    private LogoutUseCase logoutUseCase() {
      return new LogoutUseCase(singletonCImpl.authRepositoryImplProvider.get());
    }

    private GetServersUseCase getServersUseCase() {
      return new GetServersUseCase(singletonCImpl.configRepositoryImplProvider.get());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.authViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.homeViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.onboardingViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.profileViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.serversViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
      this.splashViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 5);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(MapBuilder.<String, javax.inject.Provider<ViewModel>>newMapBuilder(6).put(AuthViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) authViewModelProvider)).put(HomeViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) homeViewModelProvider)).put(OnboardingViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) onboardingViewModelProvider)).put(ProfileViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) profileViewModelProvider)).put(ServersViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) serversViewModelProvider)).put(SplashViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) splashViewModelProvider)).build());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return Collections.<Class<?>, Object>emptyMap();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.infinityconnect.vpn.ui.auth.AuthViewModel 
          return (T) new AuthViewModel(viewModelCImpl.loginAndSyncUseCase(), singletonCImpl.discoveryRepositoryImplProvider.get());

          case 1: // com.infinityconnect.vpn.ui.home.HomeViewModel 
          return (T) new HomeViewModel(viewModelCImpl.observeKeysUseCase(), viewModelCImpl.syncKeysUseCase(), singletonCImpl.vpnControllerProvider.get(), singletonCImpl.vpnStateHolderProvider.get());

          case 2: // com.infinityconnect.vpn.ui.onboarding.OnboardingViewModel 
          return (T) new OnboardingViewModel(singletonCImpl.discoveryRepositoryImplProvider.get());

          case 3: // com.infinityconnect.vpn.ui.profile.ProfileViewModel 
          return (T) new ProfileViewModel(singletonCImpl.userRepositoryImplProvider.get(), viewModelCImpl.logoutUseCase(), singletonCImpl.discoveryRepositoryImplProvider.get());

          case 4: // com.infinityconnect.vpn.ui.servers.ServersViewModel 
          return (T) new ServersViewModel(viewModelCImpl.getServersUseCase(), viewModelCImpl.savedStateHandle);

          case 5: // com.infinityconnect.vpn.ui.SplashViewModel 
          return (T) new SplashViewModel(singletonCImpl.discoveryRepositoryImplProvider.get(), singletonCImpl.authRepositoryImplProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends InfinityApp_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    private Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    private ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle 
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends InfinityApp_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    private ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }

    private BuildConnectionUseCase buildConnectionUseCase() {
      return new BuildConnectionUseCase(singletonCImpl.keysRepositoryImplProvider.get(), singletonCImpl.configRepositoryImplProvider.get(), singletonCImpl.subscriptionRepositoryImplProvider.get(), singletonCImpl.subscriptionParserProvider.get());
    }

    @Override
    public void injectInfinityVpnService(InfinityVpnService arg0) {
      injectInfinityVpnService2(arg0);
    }

    private InfinityVpnService injectInfinityVpnService2(InfinityVpnService instance) {
      InfinityVpnService_MembersInjector.injectBuildConnection(instance, buildConnectionUseCase());
      InfinityVpnService_MembersInjector.injectEngineSelector(instance, singletonCImpl.engineSelectorProvider.get());
      InfinityVpnService_MembersInjector.injectStateHolder(instance, singletonCImpl.vpnStateHolderProvider.get());
      return instance;
    }
  }

  private static final class SingletonCImpl extends InfinityApp_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    private Provider<ApiBaseUrlProvider> provideApiBaseUrlProvider;

    private Provider<TokenStorage> tokenStorageProvider;

    private Provider<HttpLoggingInterceptor> provideLoggingInterceptorProvider;

    private Provider<OkHttpClient> provideDiscoveryClientProvider;

    private Provider<Json> provideJsonProvider;

    private Provider<SessionState> sessionStateProvider;

    private Provider<KeystoreTokenProvider> keystoreTokenProvider;

    private Provider<OkHttpClient> provideApiClientProvider;

    private Provider<InfinityApi> provideInfinityApiProvider;

    private Provider<AuthRepositoryImpl> authRepositoryImplProvider;

    private Provider<KeysRepositoryImpl> keysRepositoryImplProvider;

    private Provider<DiscoveryApi> provideDiscoveryApiProvider;

    private Provider<SettingsStore> settingsStoreProvider;

    private Provider<DiscoveryRepositoryImpl> discoveryRepositoryImplProvider;

    private Provider<VpnController> vpnControllerProvider;

    private Provider<VpnStateHolder> vpnStateHolderProvider;

    private Provider<UserRepositoryImpl> userRepositoryImplProvider;

    private Provider<ConfigRepositoryImpl> configRepositoryImplProvider;

    private Provider<RawApi> provideRawApiProvider;

    private Provider<SubscriptionRepositoryImpl> subscriptionRepositoryImplProvider;

    private Provider<SubscriptionParser> subscriptionParserProvider;

    private Provider<XrayConfigBuilder> xrayConfigBuilderProvider;

    private Provider<XrayEngine> xrayEngineProvider;

    private Provider<Hysteria2Engine> hysteria2EngineProvider;

    private Provider<EngineSelector> engineSelectorProvider;

    private SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.provideApiBaseUrlProvider = DoubleCheck.provider(new SwitchingProvider<ApiBaseUrlProvider>(singletonCImpl, 3));
      this.tokenStorageProvider = DoubleCheck.provider(new SwitchingProvider<TokenStorage>(singletonCImpl, 5));
      this.provideLoggingInterceptorProvider = DoubleCheck.provider(new SwitchingProvider<HttpLoggingInterceptor>(singletonCImpl, 7));
      this.provideDiscoveryClientProvider = DoubleCheck.provider(new SwitchingProvider<OkHttpClient>(singletonCImpl, 6));
      this.provideJsonProvider = DoubleCheck.provider(new SwitchingProvider<Json>(singletonCImpl, 8));
      this.sessionStateProvider = DoubleCheck.provider(new SwitchingProvider<SessionState>(singletonCImpl, 9));
      this.keystoreTokenProvider = DoubleCheck.provider(new SwitchingProvider<KeystoreTokenProvider>(singletonCImpl, 4));
      this.provideApiClientProvider = DoubleCheck.provider(new SwitchingProvider<OkHttpClient>(singletonCImpl, 2));
      this.provideInfinityApiProvider = DoubleCheck.provider(new SwitchingProvider<InfinityApi>(singletonCImpl, 1));
      this.authRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<AuthRepositoryImpl>(singletonCImpl, 0));
      this.keysRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<KeysRepositoryImpl>(singletonCImpl, 10));
      this.provideDiscoveryApiProvider = DoubleCheck.provider(new SwitchingProvider<DiscoveryApi>(singletonCImpl, 12));
      this.settingsStoreProvider = DoubleCheck.provider(new SwitchingProvider<SettingsStore>(singletonCImpl, 13));
      this.discoveryRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<DiscoveryRepositoryImpl>(singletonCImpl, 11));
      this.vpnControllerProvider = DoubleCheck.provider(new SwitchingProvider<VpnController>(singletonCImpl, 14));
      this.vpnStateHolderProvider = DoubleCheck.provider(new SwitchingProvider<VpnStateHolder>(singletonCImpl, 15));
      this.userRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<UserRepositoryImpl>(singletonCImpl, 16));
      this.configRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<ConfigRepositoryImpl>(singletonCImpl, 17));
      this.provideRawApiProvider = DoubleCheck.provider(new SwitchingProvider<RawApi>(singletonCImpl, 19));
      this.subscriptionRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<SubscriptionRepositoryImpl>(singletonCImpl, 18));
      this.subscriptionParserProvider = DoubleCheck.provider(new SwitchingProvider<SubscriptionParser>(singletonCImpl, 20));
      this.xrayConfigBuilderProvider = DoubleCheck.provider(new SwitchingProvider<XrayConfigBuilder>(singletonCImpl, 23));
      this.xrayEngineProvider = DoubleCheck.provider(new SwitchingProvider<XrayEngine>(singletonCImpl, 22));
      this.hysteria2EngineProvider = DoubleCheck.provider(new SwitchingProvider<Hysteria2Engine>(singletonCImpl, 24));
      this.engineSelectorProvider = DoubleCheck.provider(new SwitchingProvider<EngineSelector>(singletonCImpl, 21));
    }

    @Override
    public void injectInfinityApp(InfinityApp arg0) {
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return Collections.<Boolean>emptySet();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.infinityconnect.vpn.data.repository.AuthRepositoryImpl 
          return (T) new AuthRepositoryImpl(singletonCImpl.provideInfinityApiProvider.get(), singletonCImpl.tokenStorageProvider.get(), singletonCImpl.sessionStateProvider.get());

          case 1: // com.infinityconnect.vpn.data.remote.api.InfinityApi 
          return (T) NetworkModule_ProvideInfinityApiFactory.provideInfinityApi(singletonCImpl.provideApiClientProvider.get(), singletonCImpl.provideJsonProvider.get());

          case 2: // @javax.inject.Named("api") okhttp3.OkHttpClient 
          return (T) NetworkModule_ProvideApiClientFactory.provideApiClient(singletonCImpl.provideApiBaseUrlProvider.get(), singletonCImpl.keystoreTokenProvider.get(), singletonCImpl.provideLoggingInterceptorProvider.get());

          case 3: // com.infinityconnect.vpn.data.remote.ApiBaseUrlProvider 
          return (T) NetworkModule_ProvideApiBaseUrlProviderFactory.provideApiBaseUrlProvider();

          case 4: // com.infinityconnect.vpn.data.local.KeystoreTokenProvider 
          return (T) new KeystoreTokenProvider(singletonCImpl.tokenStorageProvider.get(), singletonCImpl.provideApiBaseUrlProvider.get(), singletonCImpl.provideDiscoveryClientProvider.get(), singletonCImpl.provideJsonProvider.get(), singletonCImpl.sessionStateProvider.get());

          case 5: // com.infinityconnect.vpn.data.local.TokenStorage 
          return (T) new TokenStorage(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 6: // @javax.inject.Named("discovery") okhttp3.OkHttpClient 
          return (T) NetworkModule_ProvideDiscoveryClientFactory.provideDiscoveryClient(singletonCImpl.provideLoggingInterceptorProvider.get());

          case 7: // okhttp3.logging.HttpLoggingInterceptor 
          return (T) NetworkModule_ProvideLoggingInterceptorFactory.provideLoggingInterceptor();

          case 8: // kotlinx.serialization.json.Json 
          return (T) NetworkModule_ProvideJsonFactory.provideJson();

          case 9: // com.infinityconnect.vpn.data.local.SessionState 
          return (T) new SessionState(singletonCImpl.tokenStorageProvider.get());

          case 10: // com.infinityconnect.vpn.data.repository.KeysRepositoryImpl 
          return (T) new KeysRepositoryImpl(singletonCImpl.provideInfinityApiProvider.get());

          case 11: // com.infinityconnect.vpn.data.repository.DiscoveryRepositoryImpl 
          return (T) new DiscoveryRepositoryImpl(singletonCImpl.provideDiscoveryApiProvider.get(), singletonCImpl.settingsStoreProvider.get(), singletonCImpl.provideApiBaseUrlProvider.get());

          case 12: // com.infinityconnect.vpn.data.remote.api.DiscoveryApi 
          return (T) NetworkModule_ProvideDiscoveryApiFactory.provideDiscoveryApi(singletonCImpl.provideDiscoveryClientProvider.get(), singletonCImpl.provideJsonProvider.get());

          case 13: // com.infinityconnect.vpn.data.local.SettingsStore 
          return (T) new SettingsStore(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideJsonProvider.get());

          case 14: // com.infinityconnect.vpn.vpn.VpnController 
          return (T) new VpnController(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 15: // com.infinityconnect.vpn.vpn.VpnStateHolder 
          return (T) new VpnStateHolder();

          case 16: // com.infinityconnect.vpn.data.repository.UserRepositoryImpl 
          return (T) new UserRepositoryImpl(singletonCImpl.provideInfinityApiProvider.get());

          case 17: // com.infinityconnect.vpn.data.repository.ConfigRepositoryImpl 
          return (T) new ConfigRepositoryImpl(singletonCImpl.provideInfinityApiProvider.get());

          case 18: // com.infinityconnect.vpn.data.repository.SubscriptionRepositoryImpl 
          return (T) new SubscriptionRepositoryImpl(singletonCImpl.provideRawApiProvider.get());

          case 19: // com.infinityconnect.vpn.data.remote.api.RawApi 
          return (T) NetworkModule_ProvideRawApiFactory.provideRawApi(singletonCImpl.provideDiscoveryClientProvider.get(), singletonCImpl.provideJsonProvider.get());

          case 20: // com.infinityconnect.vpn.domain.subscription.SubscriptionParser 
          return (T) new SubscriptionParser();

          case 21: // com.infinityconnect.vpn.vpn.EngineSelector 
          return (T) new EngineSelector(singletonCImpl.xrayEngineProvider.get(), singletonCImpl.hysteria2EngineProvider.get());

          case 22: // com.infinityconnect.vpn.vpn.xray.XrayEngine 
          return (T) new XrayEngine(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.xrayConfigBuilderProvider.get());

          case 23: // com.infinityconnect.vpn.domain.engine.XrayConfigBuilder 
          return (T) new XrayConfigBuilder(singletonCImpl.provideJsonProvider.get());

          case 24: // com.infinityconnect.vpn.vpn.hysteria2.Hysteria2Engine 
          return (T) new Hysteria2Engine();

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
