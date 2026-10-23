package com.will.mvvmbase.feature.status.data

import com.will.mvvmbase.core.network.NetworkClientFactory
import com.will.mvvmbase.core.network.NetworkConfiguration
import com.will.mvvmbase.feature.status.domain.StatusResult
import javax.inject.Provider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class NetworkStatusRepositoryTest {
    @Test fun `configured endpoint decodes status into domain result`() = runBlocking {
        val baseUrl = "https://example.test/"
        val api = NetworkClientFactory.create(
            baseUrl,
            interceptors = listOf(Interceptor { chain ->
                assertEquals("https://example.test/status", chain.request().url.toString())
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"status":"ready"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }),
        ).create(StatusService::class.java)

        assertEquals(
            StatusResult.Available("ready"),
            NetworkStatusRepository(Provider { api }, NetworkConfiguration(baseUrl)).check(),
        )
    }

    @Test fun `unconfigured endpoint never sends a request`() = runBlocking {
        val api = object : StatusService {
            override suspend fun status(): Response<StatusDto> = error("Network should not be called")
        }

        assertEquals(
            StatusResult.NotConfigured,
            NetworkStatusRepository(Provider { api }, NetworkConfiguration("")).check(),
        )
    }
}
