package com.infinityconnect.vpn.di

import com.infinityconnect.vpn.data.repository.AuthRepositoryImpl
import com.infinityconnect.vpn.data.repository.ConfigRepositoryImpl
import com.infinityconnect.vpn.data.repository.DiscoveryRepositoryImpl
import com.infinityconnect.vpn.data.repository.KeysRepositoryImpl
import com.infinityconnect.vpn.data.repository.RoutingRepositoryImpl
import com.infinityconnect.vpn.data.repository.SubscriptionRepositoryImpl
import com.infinityconnect.vpn.data.repository.UserRepositoryImpl
import com.infinityconnect.vpn.domain.repository.AuthRepository
import com.infinityconnect.vpn.domain.repository.ConfigRepository
import com.infinityconnect.vpn.domain.repository.DiscoveryRepository
import com.infinityconnect.vpn.domain.repository.KeysRepository
import com.infinityconnect.vpn.domain.repository.RoutingRepository
import com.infinityconnect.vpn.domain.repository.SubscriptionRepository
import com.infinityconnect.vpn.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Привязки интерфейсов репозиториев к их реализациям. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDiscoveryRepository(impl: DiscoveryRepositoryImpl): DiscoveryRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindKeysRepository(impl: KeysRepositoryImpl): KeysRepository

    @Binds
    @Singleton
    abstract fun bindConfigRepository(impl: ConfigRepositoryImpl): ConfigRepository

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(impl: SubscriptionRepositoryImpl): SubscriptionRepository

    @Binds
    @Singleton
    abstract fun bindRoutingRepository(impl: RoutingRepositoryImpl): RoutingRepository
}
