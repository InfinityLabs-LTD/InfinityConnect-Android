package com.infinityconnect.vpn.di

import com.infinityconnect.vpn.data.local.KeystoreTokenProvider
import com.infinityconnect.vpn.data.remote.TokenProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Привязка [TokenProvider] к реализации поверх Keystore. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TokenModule {

    @Binds
    @Singleton
    abstract fun bindTokenProvider(impl: KeystoreTokenProvider): TokenProvider
}
