package com.will.mvvmbase.feature.auth.domain

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val state: StateFlow<AuthState>

    /** The current access token for authenticated network requests, when available. */
    val accessToken: String?

    /** On success, persist the session and publish [AuthState.SignedIn] before returning. */
    suspend fun login(username: String, password: String): LoginResult

    suspend fun logout()

    /** Called by the network layer when the server rejects the current session. */
    fun invalidateSession()
}
