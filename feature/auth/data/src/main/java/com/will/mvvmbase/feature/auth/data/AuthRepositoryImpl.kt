package com.will.mvvmbase.feature.auth.data

import com.will.mvvmbase.core.network.SessionTokenProvider
import com.will.mvvmbase.feature.auth.domain.AuthRemoteDataSource
import com.will.mvvmbase.feature.auth.domain.AuthRemoteResult
import com.will.mvvmbase.feature.auth.domain.AuthRepository
import com.will.mvvmbase.feature.auth.domain.AuthSessionTokens
import com.will.mvvmbase.feature.auth.domain.AuthState
import com.will.mvvmbase.feature.auth.domain.LoginResult
import com.will.mvvmbase.feature.auth.domain.SessionDataCleaner
import java.util.concurrent.CountDownLatch
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val tokenStore: SessionStore,
    private val remoteDataSource: AuthRemoteDataSource,
    private val sessionDataCleaners: Set<@JvmSuppressWildcards SessionDataCleaner> = emptySet(),
) : AuthRepository, SessionTokenProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionLock = Any()
    private val sessionLoaded = CountDownLatch(1)
    private val mutableState = MutableStateFlow<AuthState>(AuthState.Loading)
    @Volatile private var currentTokens: AuthSessionTokens? = null
    private var sessionGeneration = 0L
    private var lastRefreshFailure: Pair<String, Long>? = null
    @Volatile private var cleanupJob: Deferred<Unit>? = null

    override val state: StateFlow<AuthState> = mutableState.asStateFlow()
    override val accessToken: String?
        get() {
            try {
                sessionLoaded.await()
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
            return currentTokens?.accessToken
        }

    init {
        scope.launch {
            synchronized(sessionLock) {
                val restoredTokens = runCatching { tokenStore.readSession() }.getOrNull()
                currentTokens = restoredTokens
                mutableState.value = if (restoredTokens == null) {
                    AuthState.SignedOut
                } else {
                    AuthState.SignedIn
                }
                sessionLoaded.countDown()
            }
        }
    }

    override suspend fun login(username: String, password: String): LoginResult {
        val generation = synchronized(sessionLock) { ++sessionGeneration }
        return when (val result = try { remoteDataSource.login(username, password) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { AuthRemoteResult.Failure(failure.message ?: "登录失败，请稍后重试。") }) {
            is AuthRemoteResult.Failure -> LoginResult.Failure(result.message)
            is AuthRemoteResult.Success -> {
                val validTokens = result.tokens.withoutBlankTokens()
                if (validTokens == null) {
                    LoginResult.Failure("认证服务返回的令牌无效。")
                } else {
                    runCatching { setSession(validTokens, generation) }
                        .fold(
                            onSuccess = { LoginResult.Success },
                            onFailure = { if (it is CancellationException) throw it
                                LoginResult.Failure(it.message ?: "保存登录状态失败。") },
                        )
                }
            }
        }

    }

    /** Serializes refreshes and reuses a token another request has already refreshed. */
    override fun refreshAccessToken(rejectedAccessToken: String): String? = synchronized(sessionLock) {
        val current = currentTokens ?: return@synchronized null
        if (current.accessToken != rejectedAccessToken) return@synchronized current.accessToken
        val now = System.nanoTime()
        val recentFailure = lastRefreshFailure
        if (recentFailure?.first == rejectedAccessToken &&
            now - recentFailure.second < REFRESH_FAILURE_BACKOFF_NANOS
        ) {
            return@synchronized null
        }

        val refreshToken = current.refreshToken ?: run {
            clearSessionLocked()
            return@synchronized null
        }
        val refreshed = runCatching { remoteDataSource.refresh(refreshToken) }
            .getOrElse { AuthRemoteResult.Failure(it.message ?: "刷新登录状态失败。") }
        when (refreshed) {
            is AuthRemoteResult.Failure -> {
                if (refreshed.sessionInvalid) {
                    clearSessionLocked()
                } else {
                    lastRefreshFailure = rejectedAccessToken to System.nanoTime()
                }
                null
            }
            is AuthRemoteResult.Success -> {
                val newTokens = refreshed.tokens.copy(
                    refreshToken = refreshed.tokens.refreshToken ?: refreshToken,
                ).withoutBlankTokens()
                if (newTokens == null) {
                    clearSessionLocked()
                    null
                } else if (newTokens.accessToken == rejectedAccessToken) {
                    clearSessionLocked()
                    null
                } else {
                    runCatching { tokenStore.saveSession(newTokens) }
                        .fold(
                            onSuccess = {
                                currentTokens = newTokens
                                lastRefreshFailure = null
                                mutableState.value = AuthState.SignedIn
                                newTokens.accessToken
                            },
                            onFailure = {
                                clearSessionLocked()
                                null
                            },
                        )
                }
            }
        }
    }

    override suspend fun logout() {
        withContext(Dispatchers.IO) {
            synchronized(sessionLock) { clearSessionLocked() }
            cleanupJob?.await()
        }
    }

    override fun invalidateSession() {
        synchronized(sessionLock) { clearSessionLocked() }
    }

    private suspend fun setSession(tokens: AuthSessionTokens, generation: Long) {
        cleanupJob?.await()
        withContext(Dispatchers.IO) {
            synchronized(sessionLock) {
                check(sessionGeneration == generation) { "登录操作已取消" }
                tokenStore.saveSession(tokens)
                currentTokens = tokens
                lastRefreshFailure = null
                mutableState.value = AuthState.SignedIn
            }
        }
    }

    private fun clearSessionLocked() {
        sessionGeneration++
        currentTokens = null
        lastRefreshFailure = null
        runCatching { tokenStore.clear() }
        mutableState.value = AuthState.SignedOut
        if (sessionDataCleaners.isNotEmpty()) {
            val previous = cleanupJob
            cleanupJob = scope.async {
                var firstFailure: Exception? = null
                try {
                    previous?.await()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    firstFailure = failure
                }
                sessionDataCleaners.forEach { cleaner ->
                    try {
                        cleaner.clearSessionData()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        if (firstFailure == null) firstFailure = failure
                    }
                }
                firstFailure?.let { throw it }
            }
        }
    }

    private fun AuthSessionTokens.withoutBlankTokens(): AuthSessionTokens? {
        val cleanAccessToken = accessToken.trim().takeIf(String::isNotEmpty) ?: return null
        val cleanRefreshToken = refreshToken?.trim()?.takeIf(String::isNotEmpty)
        return AuthSessionTokens(cleanAccessToken, cleanRefreshToken)
    }

    private companion object {
        const val REFRESH_FAILURE_BACKOFF_NANOS = 2_000_000_000L
    }
}
