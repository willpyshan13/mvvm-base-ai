package com.will.mvvmbase

import com.will.mvvmbase.feature.auth.domain.*
import com.will.mvvmbase.feature.auth.data.UnconfiguredAuthRemoteDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthBackendModule {
    @Provides @Singleton
    fun backend(
        unconfigured: UnconfiguredAuthRemoteDataSource,
        commonHeadersProvider: AppCommonHeadersProvider,
    ): AuthRemoteDataSource = when {
        BuildConfig.DEBUG && BuildConfig.DEMO_AUTH -> DemoAuthRemoteDataSource()
        BuildConfig.API_BASE_URL.isNotBlank() -> com.will.mvvmbase.feature.auth.data.HttpAuthRemoteDataSource(
            com.will.mvvmbase.core.network.NetworkClientFactory.create(
                BuildConfig.API_BASE_URL,
                commonHeadersProvider = commonHeadersProvider,
                networkInterceptors = listOfNotNull(
                    com.will.mvvmbase.core.apm.AppApm.networkInterceptor(BuildConfig.API_BASE_URL),
                ),
                callFactoryDecorator = {
                    com.will.mvvmbase.core.apm.AppApm.instrumentCallFactory(it, BuildConfig.API_BASE_URL)
                },
            )
                .create(com.will.mvvmbase.feature.auth.data.AuthService::class.java))
        else -> unconfigured
    }
}

/** Explicit opt-in demo. These credentials/tokens must never authenticate real HTTP requests. */
internal class DemoAuthRemoteDataSource : AuthRemoteDataSource {
    override suspend fun login(username: String, password: String): AuthRemoteResult =
        if (username == "demo" && password == "demo1234") {
            AuthRemoteResult.Success(AuthSessionTokens("demo-access", "demo-refresh"))
        } else AuthRemoteResult.Failure("演示账号：demo，密码：demo1234")

    override fun refresh(refreshToken: String): AuthRemoteResult =
        if (refreshToken == "demo-refresh") {
            AuthRemoteResult.Success(AuthSessionTokens("demo-access-${System.nanoTime()}", refreshToken))
        } else AuthRemoteResult.Failure("演示会话已失效", sessionInvalid = true)
}
