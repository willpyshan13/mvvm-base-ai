package com.will.mvvmbase.core.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/** Supplies defaults; explicit endpoint headers take precedence (case insensitive). */
internal class CommonHeadersInterceptor(
    private val origin: HttpUrl,
    private val provider: CommonHeadersProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.hasSameOriginAs(origin)) return chain.proceed(request)
        val builder = request.newBuilder()
        val names = mutableSetOf<String>()
        provider.headers().forEach { (name, value) ->
            require(name.lowercase(java.util.Locale.ROOT) !in reserved) {
                "Header $name must be configured through authentication or the request body"
            }
            // OkHttp validates header names and values, including CR/LF injection.
            if (request.header(name) == null) builder.header(name, value)
            names += name
        }
        return chain.proceed(builder.tag(CommonHeaderNames::class.java, CommonHeaderNames(names)).build())
    }

    private companion object {
        val reserved = setOf("authorization", "proxy-authorization", "cookie", "host",
            "content-type", "content-length", "transfer-encoding", "connection",
            "accept-encoding", "range", "if-range")
    }
}

internal data class CommonHeaderNames(val names: Set<String>)

/** Redirect follow-ups bypass application interceptors; strip defaults at the network boundary. */
internal class CommonHeadersOriginInterceptor(private val origin: HttpUrl) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.hasSameOriginAs(origin)) return chain.proceed(request)
        val builder = request.newBuilder()
        request.tag(CommonHeaderNames::class.java)?.names?.forEach { builder.removeHeader(it) }
        builder.removeHeader("Authorization")
        return chain.proceed(builder.build())
    }
}
