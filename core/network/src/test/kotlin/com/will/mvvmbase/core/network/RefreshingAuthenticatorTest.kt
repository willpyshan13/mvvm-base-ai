package com.will.mvvmbase.core.network

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefreshingAuthenticatorTest {
    @Test
    fun `concurrent 401 responses share one successful refresh`() {
        val provider = FakeSessionTokenProvider()
        val authenticator = RefreshingAuthenticator(provider, "https://example.test/".toHttpUrl())
        val executor = Executors.newFixedThreadPool(2)
        try {
            val responses = (1..2).map { unauthorizedResponse() }
            val retriedRequests = responses.map { response ->
                executor.submit<Request?> { authenticator.authenticate(null, response) }
            }.map { it.get(5, TimeUnit.SECONDS) }

            assertEquals(1, provider.refreshCount.get())
            assertEquals(listOf("Bearer fresh-token", "Bearer fresh-token"), retriedRequests.map {
                it?.header("Authorization")
            })
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `request without bearer authorization does not refresh`() {
        val provider = FakeSessionTokenProvider()
        val authenticator = RefreshingAuthenticator(provider, "https://example.test/".toHttpUrl())
        val request = Request.Builder().url("https://example.test/resource").build()
        val response = unauthorizedResponse(request)

        assertNull(authenticator.authenticate(null, response))
        assertEquals(0, provider.refreshCount.get())
    }

    @Test
    fun `unauthorized response from another origin does not refresh session`() {
        val provider = FakeSessionTokenProvider()
        val authenticator = RefreshingAuthenticator(provider, "https://example.test/".toHttpUrl())
        val request = Request.Builder()
            .url("https://outside.test/resource")
            .header("Authorization", "Bearer old-token")
            .build()

        assertNull(authenticator.authenticate(null, unauthorizedResponse(request)))
        assertEquals(0, provider.refreshCount.get())
    }

    private fun unauthorizedResponse(request: Request = Request.Builder()
            .url("https://example.test/resource")
            .header("Authorization", "Bearer old-token")
            .build()): Response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("".toResponseBody())
            .build()

    private class FakeSessionTokenProvider : SessionTokenProvider {
        private val mutableToken = java.util.concurrent.atomic.AtomicReference<String?>("old-token")
        val refreshCount = AtomicInteger()

        override val accessToken: String? get() = mutableToken.get()

        override fun refreshAccessToken(rejectedAccessToken: String): String? {
            refreshCount.incrementAndGet()
            Thread.sleep(50)
            mutableToken.set("fresh-token")
            return "fresh-token"
        }
    }
}
