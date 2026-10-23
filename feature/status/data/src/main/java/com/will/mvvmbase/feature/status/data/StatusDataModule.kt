package com.will.mvvmbase.feature.status.data

import com.will.mvvmbase.feature.status.domain.StatusRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class StatusRepositoryBindings {
    @Binds
    abstract fun bindStatusRepository(implementation: NetworkStatusRepository): StatusRepository
}

@Module
@InstallIn(SingletonComponent::class)
object StatusServiceModule {
    @Provides
    @Singleton
    fun provideStatusService(retrofit: Retrofit): StatusService =
        retrofit.create(StatusService::class.java)
}
