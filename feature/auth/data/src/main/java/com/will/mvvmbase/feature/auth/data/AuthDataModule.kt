package com.will.mvvmbase.feature.auth.data

import com.will.mvvmbase.feature.auth.domain.AuthRepository
import com.will.mvvmbase.feature.auth.domain.AuthRemoteDataSource
import com.will.mvvmbase.feature.auth.domain.SessionDataCleaner
import com.will.mvvmbase.core.network.SessionTokenProvider
import dagger.Binds
import dagger.Module
import dagger.multibindings.Multibinds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthDataModule {
    @Multibinds
    abstract fun sessionDataCleaners(): Set<SessionDataCleaner>

    @Binds @Singleton
    abstract fun store(implementation: EncryptedTokenStore): SessionStore

    @Binds
    @Singleton
    abstract fun bindAuthRepository(implementation: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSessionTokenProvider(implementation: AuthRepositoryImpl): SessionTokenProvider

}
