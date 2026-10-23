package com.will.mvvmbase.feature.auth.data

import com.will.mvvmbase.feature.auth.domain.*
import kotlinx.serialization.Serializable
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** Reference protocol; use a separate unauthenticated Retrofit instance to avoid refresh recursion. */
class HttpAuthRemoteDataSource(private val service: AuthService) : AuthRemoteDataSource {
    override suspend fun login(username: String, password: String) =
        service.login(LoginRequest(username, password)).toResult(false)
    override fun refresh(refreshToken: String) = service.refresh(RefreshRequest(refreshToken)).execute().toResult(true)
    private fun Response<TokenResponse>.toResult(refresh: Boolean): AuthRemoteResult {
        if (!isSuccessful) {
            errorBody()?.close()
            return AuthRemoteResult.Failure("认证请求失败（HTTP ${code()}）", sessionInvalid = refresh && code() in listOf(401, 403))
        }
        val tokens = body() ?: return AuthRemoteResult.Failure("认证服务返回空数据")
        return AuthRemoteResult.Success(AuthSessionTokens(tokens.accessToken, tokens.refreshToken))
    }
}
