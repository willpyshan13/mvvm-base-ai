package com.will.mvvmbase.core.network

/** Supplies the current session token and performs a synchronous refresh for OkHttp's authenticator. */
interface SessionTokenProvider {
    val accessToken: String?

    /** Returns a replacement access token, or null when the request cannot be retried. */
    fun refreshAccessToken(rejectedAccessToken: String): String?
}
