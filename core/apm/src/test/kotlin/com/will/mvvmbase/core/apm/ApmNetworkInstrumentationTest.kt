package com.will.mvvmbase.core.apm

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.context.Context
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ApmNetworkInstrumentationTest {
    private val exporter = InMemorySpanExporter.create()
    private val provider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
        .build()
    private val telemetry = OpenTelemetrySdk.builder().setTracerProvider(provider).build()

    @After
    fun close() { telemetry.close() }

    @Test
    fun apiSpanEndsAfterBodyConsumptionAndOmitsSensitiveUrlData() {
        var traceparent: String? = null
        val client = client(external = false) { request ->
            traceparent = request.header("traceparent")
            response(request, "payload".toResponseBody())
        }
        val response = client.newCall(Request.Builder()
            .url("https://api.example.test/private/42?token=secret").build()).execute()
        assertTrue(exporter.finishedSpanItems.isEmpty())
        assertNotNull(traceparent)
        response.use { assertEquals("payload", it.body.string()) }
        val spans = exporter.finishedSpanItems
        assertEquals(1, spans.size)
        assertFalse(spans.single().attributes.toString().contains("secret"))
        assertFalse(spans.single().attributes.toString().contains("private"))
        assertEquals(200L, spans.single().attributes.get(AttributeKey.longKey("http.response.status_code")))
    }

    @Test
    fun externalHttpsIsObservedWithoutTraceHeaderPropagation() {
        var traceparent: String? = null
        client(external = true) { request ->
            traceparent = request.header("traceparent")
            response(request, "image".toResponseBody())
        }.newCall(Request.Builder().url("https://cdn.example.test/image?signature=secret").build())
            .execute().use { it.body.string() }
        assertNull(traceparent)
        assertEquals(1, exporter.finishedSpanItems.size)
    }

    @Test
    fun apiInterceptorDoesNotInstrumentOtherOrigins() {
        client(external = false) { request ->
            assertNull(request.header("traceparent"))
            response(request, "data".toResponseBody())
        }.newCall(Request.Builder().url("https://outside.example.test/file").build())
            .execute().close()
        assertTrue(exporter.finishedSpanItems.isEmpty())
    }

    @Test
    fun requestUsesCapturedBusinessParentEvenOutsideItsThreadScope() {
        val parent = telemetry.getTracer("test").spanBuilder("business.operation").startSpan()
        try {
            client(external = false) { response(it, "data".toResponseBody()) }
                .newCall(Request.Builder().url("https://api.example.test/status")
                    .tag(CallContextTag::class.java, CallContextTag(Context.root().with(parent)))
                    .build()).execute().use { it.body.string() }
            assertEquals(parent.spanContext.spanId, exporter.finishedSpanItems.single().parentSpanId)
            assertEquals(parent.spanContext.traceId, exporter.finishedSpanItems.single().traceId)
        } finally {
            parent.end()
        }
    }

    @Test
    fun redirectedRequestDropsHeaderPreviouslyInjectedForApiOrigin() {
        val injected = "00-12345678901234567890123456789012-1234567890123456-01"
        client(external = false) { request ->
            assertNull(request.header("traceparent"))
            response(request, "data".toResponseBody())
        }.newCall(Request.Builder().url("https://outside.example.test/redirect")
            .header("traceparent", injected)
            .tag(InjectedTraceparentTag::class.java, InjectedTraceparentTag(injected))
            .build()).execute().close()
        assertTrue(exporter.finishedSpanItems.isEmpty())
    }

    @Test
    fun connectionFailureEndsSpanWithError() {
        try {
            client(external = false) { throw IOException("offline") }
                .newCall(Request.Builder().url("https://api.example.test/status").build()).execute()
            fail("Expected an I/O failure")
        } catch (_: IOException) { }
        assertEquals(1, exporter.finishedSpanItems.size)
        assertEquals(StatusCode.ERROR, exporter.finishedSpanItems.single().status.statusCode)
    }

    @Test
    fun bodyReadFailureEndsSpanOnceWithError() {
        val brokenBody = object : ResponseBody() {
            private val input = object : Source {
                override fun read(sink: Buffer, byteCount: Long): Long = throw IOException("offline")
                override fun timeout(): Timeout = Timeout.NONE
                override fun close() = Unit
            }.buffer()
            override fun contentType() = null
            override fun contentLength() = -1L
            override fun source() = input
        }
        val response = client(external = false) { response(it, brokenBody) }
            .newCall(Request.Builder().url("https://api.example.test/file").build()).execute()
        try {
            response.use { it.body.string() }
            fail("Expected an I/O failure")
        } catch (_: IOException) { }
        assertEquals(1, exporter.finishedSpanItems.size)
        assertEquals(StatusCode.ERROR, exporter.finishedSpanItems.single().status.statusCode)
    }

    @Test
    fun ffmpegHttpsUsesMediaClientTypeWithoutPropagatingTraceHeaders() {
        client(external = true, clientType = "media") { request ->
            assertNull(request.header("traceparent"))
            response(request, "video".toResponseBody())
        }.newCall(Request.Builder().url("https://cdn.example.test/video?signature=secret").build())
            .execute().use { it.body.string() }
        val span = exporter.finishedSpanItems.single()
        assertEquals("media", span.attributes.get(AttributeKey.stringKey("app.http.client.type")))
        assertFalse(span.attributes.toString().contains("signature"))
    }

    private fun client(external: Boolean, clientType: String = "image", transport: (Request) -> Response): OkHttpClient {
        val interceptor = requireNotNull(ApmNetworkInstrumentation.createNetworkInterceptor(
            apiOrigin = if (external) null else "https://api.example.test/".toHttpUrl(),
            externalClientType = if (external) clientType else null,
            openTelemetry = telemetry,
        ))
        // The terminal interceptor avoids real sockets while exercising the production interceptor.
        return OkHttpClient.Builder().addInterceptor(interceptor)
            .addInterceptor(Interceptor { transport(it.request()) }).build()
    }

    private fun response(request: Request, body: ResponseBody) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
}
