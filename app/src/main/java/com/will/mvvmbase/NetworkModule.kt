package com.will.mvvmbase

import com.will.mvvmbase.core.apm.AppApm
import com.will.mvvmbase.core.network.NetworkClientFactory
import com.will.mvvmbase.core.network.NetworkConfiguration
import com.will.mvvmbase.core.network.SessionTokenProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideNetworkConfiguration(): NetworkConfiguration =
        NetworkConfiguration(BuildConfig.API_BASE_URL)

    @Provides
    @Singleton
    fun provideRetrofit(
        configuration: NetworkConfiguration,
        sessionTokenProvider: SessionTokenProvider,
        commonHeadersProvider: AppCommonHeadersProvider,
    ): Retrofit {
        check(configuration.isConfigured) {
            "Set -PapiBaseUrl=https://your-api.example/ before injecting Retrofit"
        }
        return NetworkClientFactory.create(
            baseUrl = configuration.baseUrl,
            commonHeadersProvider = commonHeadersProvider,
            networkInterceptors = listOfNotNull(AppApm.networkInterceptor(configuration.baseUrl)),
            callFactoryDecorator = { AppApm.instrumentCallFactory(it, configuration.baseUrl) },
            interceptors = if (BuildConfig.DEBUG) listOf(com.will.mvvmbase.core.network.NetworkDiagnostics.interceptor) else emptyList(),
            sessionTokenProvider = sessionTokenProvider.takeUnless { BuildConfig.DEBUG && BuildConfig.DEMO_AUTH },
        )
    }
}

