package com.will.mvvmbase.feature.auth.domain

/** Backend-specific auth contract. Implement this with the server's actual paths and payloads. */
interface AuthRemoteDataSource {
    suspend fun login(username: String, password: String): AuthRemoteResult

    /**
     * Runs from OkHttp's background authenticator thread. Use a separate, unauthenticated client
     * and a blocking request here so refreshing does not re-enter the protected client.
     */
    fun refresh(refreshToken: String): AuthRemoteResult
}

sealed interface AuthRemoteResult {
    data class Success(val tokens: AuthSessionTokens) : AuthRemoteResult
    data class Failure(
        val message: String,
        /** True only when the server confirms the refresh token/session is no longer valid. */
        val sessionInvalid: Boolean = false,
    ) : AuthRemoteResult
}
