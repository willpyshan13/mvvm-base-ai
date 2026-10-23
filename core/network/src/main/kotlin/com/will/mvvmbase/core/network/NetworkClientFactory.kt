package com.will.mvvmbase.core.network

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Creates clients for a configured API. Supply the base URL from the consuming app. */
object NetworkClientFactory {
    fun create(
        baseUrl: String,
        tokenProvider: () -> String? = { null },
        interceptors: List<Interceptor> = emptyList(),
        onUnauthorized: () -> Unit = {},
        sessionTokenProvider: SessionTokenProvider? = null,
        commonHeadersProvider: CommonHeadersProvider = CommonHeadersProvider { emptyMap() },
        callFactoryDecorator: (okhttp3.Call.Factory) -> okhttp3.Call.Factory = { it },
        networkInterceptors: List<Interceptor> = emptyList(),
    ): Retrofit {
        val apiOrigin = baseUrl.toHttpUrl()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(CommonHeadersInterceptor(apiOrigin, commonHeadersProvider))
            .addNetworkInterceptor(CommonHeadersOriginInterceptor(apiOrigin))
            .addInterceptor { chain ->
                val request = chain.request()
                val token = if (request.url.hasSameOriginAs(apiOrigin)) {
                    (sessionTokenProvider?.accessToken ?: tokenProvider())?.trim()
                } else {
                    null
                }
                val authenticatedRequest = request.newBuilder().apply {
                    if (request.header("Authorization") == null && !token.isNullOrEmpty()) {
                        header("Authorization", "Bearer $token")
                    }
                }.build()
                val response = chain.proceed(authenticatedRequest)
                if (response.code == 401 &&
                    request.url.hasSameOriginAs(apiOrigin) &&
                    sessionTokenProvider == null
                ) {
                    onUnauthorized()
                }
                response
            }
            .authenticator(
                sessionTokenProvider?.let { RefreshingAuthenticator(it, apiOrigin) }
                    ?: Authenticator.NONE,
            )
            .apply { networkInterceptors.forEach(::addNetworkInterceptor) }
            .apply { interceptors.forEach(::addInterceptor) }
            .build()

        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .callFactory(callFactoryDecorator(client))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

}

internal class RefreshingAuthenticator(
    private val sessionTokenProvider: SessionTokenProvider,
    private val apiOrigin: HttpUrl,
) : Authenticator {
    private val refreshLock = Any()

    override fun authenticate(route: okhttp3.Route?, response: Response): Request? {
        if (responseCount(response) > 1) return null
        if (!response.request.url.hasSameOriginAs(apiOrigin)) return null

        val authorization = response.request.header("Authorization") ?: return null
        if (!authorization.startsWith("Bearer ", ignoreCase = true)) return null
        val rejectedToken = authorization.substringAfter(' ', "").trim().takeIf(String::isNotEmpty)
            ?: return null

        val newToken = synchronized(refreshLock) {
            val currentToken = sessionTokenProvider.accessToken?.trim()
            if (!currentToken.isNullOrEmpty() && currentToken != rejectedToken) {
                currentToken
            } else {
                sessionTokenProvider.refreshAccessToken(rejectedToken)
            }
        }?.trim()?.takeIf(String::isNotEmpty) ?: return null

        if (newToken == rejectedToken) return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

internal fun HttpUrl.hasSameOriginAs(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port

