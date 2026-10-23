package com.will.mvvmbase.feature.auth.data

import com.will.mvvmbase.feature.auth.domain.AuthRemoteDataSource
import com.will.mvvmbase.feature.auth.domain.AuthRemoteResult
import javax.inject.Inject
import javax.inject.Singleton

/** Safe default until the backend-specific login and refresh protocol is supplied. */
@Singleton
class UnconfiguredAuthRemoteDataSource @Inject constructor() : AuthRemoteDataSource {
    override suspend fun login(username: String, password: String): AuthRemoteResult =
        AuthRemoteResult.Failure("登录接口尚未配置，请先接入服务端认证协议。")

    override fun refresh(refreshToken: String): AuthRemoteResult =
        AuthRemoteResult.Failure("令牌刷新接口尚未配置。", sessionInvalid = true)
}
