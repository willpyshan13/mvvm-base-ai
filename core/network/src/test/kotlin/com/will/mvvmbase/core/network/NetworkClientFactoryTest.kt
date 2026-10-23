package com.will.mvvmbase.core.network

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import retrofit2.http.GET

class NetworkClientFactoryTest {
    @Serializable private data class StatusDto(val status: String)

    private interface StatusApi {
        @GET("status")
        suspend fun status(): Response<StatusDto>
    }

    private interface ExternalStatusApi {
        @GET("https://outside.test/status")
        suspend fun status(): Response<StatusDto>
    }

    @Test fun `client adds auth header and decodes JSON`() = runBlocking {
        val capturedRequest = AtomicReference<Request>()
        val transport = Interceptor { chain ->
            capturedRequest.set(chain.request())
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"status":"ready","ignored":true}""".toResponseBody("application/json".toMediaType()))
                .build()
        }
        val api = NetworkClientFactory.create(
            baseUrl = "https://example.test/",
            tokenProvider = { "test-token" },
            interceptors = listOf(transport),
        ).create(StatusApi::class.java)

        assertEquals(ApiResult.Success(StatusDto("ready")), apiCall { api.status() })
        assertEquals("Bearer test-token", capturedRequest.get().header("Authorization"))
        assertEquals("https://example.test/status", capturedRequest.get().url.toString())
    }

    @Test fun `HTTP and network failures have distinct results`() = runBlocking {
        val httpApi = NetworkClientFactory.create(
            "https://example.test/",
            interceptors = listOf(Interceptor { chain ->
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Unavailable")
                    .body("".toResponseBody())
                    .build()
            }),
        ).create(StatusApi::class.java)
        assertEquals(ApiResult.HttpError(503, "Unavailable"), apiCall { httpApi.status() })

        val offlineApi = NetworkClientFactory.create(
            "https://example.test/",
            interceptors = listOf(Interceptor { throw IOException("offline") }),
        ).create(StatusApi::class.java)
        assertTrue(apiCall { offlineApi.status() } is ApiResult.NetworkError)
    }

    @Test fun `client does not send session token to another origin`() = runBlocking {
        val capturedRequest = AtomicReference<Request>()
        val transport = Interceptor { chain ->
            capturedRequest.set(chain.request())
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"status":"ready"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }
        val api = NetworkClientFactory.create(
            baseUrl = "https://example.test/",
            tokenProvider = { "private-token" },
            interceptors = listOf(transport),
        ).create(ExternalStatusApi::class.java)

        assertEquals(ApiResult.Success(StatusDto("ready")), apiCall { api.status() })
        assertEquals("https://outside.test/status", capturedRequest.get().url.toString())
        assertEquals(null, capturedRequest.get().header("Authorization"))
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is propagated`(): Unit = runBlocking {
        apiCall<String> { throw CancellationException("cancelled") }
    }
}
