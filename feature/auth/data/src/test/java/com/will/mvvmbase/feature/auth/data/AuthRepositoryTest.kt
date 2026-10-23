package com.will.mvvmbase.feature.auth.data

import com.will.mvvmbase.feature.auth.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class AuthRepositoryTest {
    @Test fun `logout waits for feature data cleanup`() = runBlocking {
        val completed = CompletableDeferred<Unit>()
        val cleaner = object : SessionDataCleaner {
            override suspend fun clearSessionData() { completed.await() }
        }
        val repo = AuthRepositoryImpl(MemoryStore(AuthSessionTokens("old", "refresh")), Backend(), setOf(cleaner))
        repo.ready()
        val logout = async { repo.logout() }
        withTimeout(2000) { repo.state.first { it == AuthState.SignedOut } }
        assertFalse(logout.isCompleted)
        completed.complete(Unit)
        withTimeout(2000) { logout.await() }
    }
    private class MemoryStore(var tokens: AuthSessionTokens? = null) : SessionStore {
        override fun readSession() = tokens
        override fun saveSession(tokens: AuthSessionTokens) { this.tokens = tokens }
        override fun clear() { tokens = null }
    }
    private class Backend : AuthRemoteDataSource {
        var loginBlock: suspend () -> AuthRemoteResult = { AuthRemoteResult.Success(AuthSessionTokens("access", "refresh")) }
        var refreshResult: AuthRemoteResult = AuthRemoteResult.Failure("offline")
        override suspend fun login(username: String, password: String) = loginBlock()
        override fun refresh(refreshToken: String) = refreshResult
    }
    private suspend fun AuthRepositoryImpl.ready() = withTimeout(2000) { state.first { it != AuthState.Loading } }

    @Test fun `logout prevents a delayed login from resurrecting session`() = runBlocking {
        val store = MemoryStore()
        val backend = Backend()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        backend.loginBlock = { started.complete(Unit); finish.await(); AuthRemoteResult.Success(AuthSessionTokens("late", "refresh")) }
        val repo = AuthRepositoryImpl(store, backend); repo.ready()
        val login = async { repo.login("demo", "password") }
        started.await(); repo.logout(); finish.complete(Unit)
        assertTrue(login.await() is LoginResult.Failure)
        assertEquals(AuthState.SignedOut, repo.state.value)
        assertNull(store.tokens)
    }
    @Test fun `network refresh failure retains session but revoked credentials clear it`() = runBlocking {
        val store = MemoryStore(AuthSessionTokens("old", "refresh"))
        val backend = Backend()
        val repo = AuthRepositoryImpl(store, backend); repo.ready()
        assertNull(repo.refreshAccessToken("old"))
        assertEquals(AuthState.SignedIn, repo.state.value)
        assertNotNull(store.tokens)
        // Recreate repository to avoid the transient-failure backoff for the second case.
        val revoked = Backend().apply { refreshResult = AuthRemoteResult.Failure("revoked", true) }
        val repo2 = AuthRepositoryImpl(store, revoked); repo2.ready()
        assertNull(repo2.refreshAccessToken("old"))
        assertEquals(AuthState.SignedOut, repo2.state.value)
        assertNull(store.tokens)
    }
    @Test fun `successful refresh persists rotated tokens and reuses them`() = runBlocking {
        val store = MemoryStore(AuthSessionTokens("old", "refresh"))
        val backend = Backend().apply { refreshResult = AuthRemoteResult.Success(AuthSessionTokens("new", "rotated")) }
        val repo = AuthRepositoryImpl(store, backend); repo.ready()
        assertEquals("new", repo.refreshAccessToken("old"))
        assertEquals("rotated", store.tokens?.refreshToken)
        assertEquals("new", repo.refreshAccessToken("old"))
    }
    @Test fun `cancellation is never converted to a login failure`() = runBlocking {
        val repo = AuthRepositoryImpl(MemoryStore(), Backend().apply { loginBlock = { throw CancellationException() } })
        repo.ready()
        try { repo.login("demo", "password"); fail("Expected cancellation") }
        catch (_: CancellationException) { }
    }
}
