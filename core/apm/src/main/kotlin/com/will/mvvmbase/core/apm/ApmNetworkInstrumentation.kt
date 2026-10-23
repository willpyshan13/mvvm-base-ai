package com.will.mvvmbase.core.apm

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context as OtelContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

/** Retrofit/OkHttp integration with origin allowlisting and response-body-aware span completion. */
internal object ApmNetworkInstrumentation {
    private const val INSTRUMENTATION_SCOPE = "com.will.mvvmbase"

    fun instrumentCallFactory(delegate: Call.Factory, apiBaseUrl: String): Call.Factory {
        val apiOrigin = apiBaseUrl.toHttpUrlOrNull() ?: return delegate
        if (ApmRuntime.openTelemetry() == null) return delegate
        return contextCapturingCallFactory(delegate) { request -> request.url.hasSameOriginAs(apiOrigin) }
    }

    /** Captures parent context without enabling trace propagation to third-party image hosts. */
    fun instrumentCallFactory(delegate: Call.Factory): Call.Factory {
        if (ApmRuntime.openTelemetry() == null) return delegate
        return contextCapturingCallFactory(delegate) { true }
    }

    private fun contextCapturingCallFactory(
        delegate: Call.Factory,
        shouldCapture: (okhttp3.Request) -> Boolean,
    ): Call.Factory = object : Call.Factory {
        override fun newCall(request: okhttp3.Request): Call {
            val requestWithContext = if (shouldCapture(request)) {
                request.newBuilder()
                    .tag(CallContextTag::class.java, CallContextTag(OtelContext.current()))
                    .build()
            } else {
                request
            }
            return delegate.newCall(requestWithContext)
        }
    }

    fun imageNetworkInterceptor(): Interceptor? = externalHttpsNetworkInterceptor("image")

    fun externalHttpsNetworkInterceptor(clientType: String): Interceptor? {
        if (clientType !in setOf("image", "file_transfer", "media")) return null
        return createNetworkInterceptor(apiOrigin = null, externalClientType = clientType)
    }

    fun networkInterceptor(apiBaseUrl: String): Interceptor? {
        val apiOrigin = apiBaseUrl.toHttpUrlOrNull() ?: return null
        return createNetworkInterceptor(apiOrigin, externalClientType = null)
    }

    internal fun createNetworkInterceptor(
        apiOrigin: HttpUrl?,
        externalClientType: String?,
        openTelemetry: OpenTelemetry? = ApmRuntime.openTelemetry(),
    ): Interceptor? {
        val activeOpenTelemetry = openTelemetry ?: return null
        val clientType = externalClientType ?: "api"
        val activeTracer = activeOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE)
        val meter = activeOpenTelemetry.getMeter(INSTRUMENTATION_SCOPE)
        val requestCount = meter.counterBuilder("app.http.client.request.count")
            .setUnit("{request}")
            .build()
        val errorCount = meter.counterBuilder("app.http.client.request.error.count")
            .setUnit("{request}")
            .build()
        val duration = meter.histogramBuilder("http.client.request.duration")
            .setUnit("s")
            .build()
        val requestBodySize = meter.histogramBuilder("http.client.request.body.size")
            .setUnit("By")
            .build()
        val responseBodySize = meter.histogramBuilder("http.client.response.body.size")
            .setUnit("By")
            .build()

        return Interceptor { chain ->
            val request = chain.request()
            val isInstrumentedOrigin = when {
                apiOrigin != null -> request.url.hasSameOriginAs(apiOrigin)
                externalClientType != null -> request.url.isHttps
                else -> false
            }
            if (!isInstrumentedOrigin) {
                val injectedHeader = request.tag(InjectedTraceparentTag::class.java)
                val safeRequest = if (injectedHeader != null && request.header("traceparent") == injectedHeader.value) {
                    request.newBuilder()
                        .removeHeader("traceparent")
                        .tag(InjectedTraceparentTag::class.java, null)
                        .build()
                } else {
                    request
                }
                return@Interceptor chain.proceed(safeRequest)
            }

            val method = request.method
            val host = request.url.host
            val parentContext = request.tag(CallContextTag::class.java)?.context ?: OtelContext.current()
            val span = activeTracer.spanBuilder("HTTP $method")
                .setSpanKind(SpanKind.CLIENT)
                .setParent(parentContext)
                .startSpan()
            val startNanos = System.nanoTime()
            var outcome = "success"
            var responseStatusCode: Int? = null
            val completed = AtomicBoolean(false)

            fun finishRequest(responseBytesRead: Long? = null) {
                if (!completed.compareAndSet(false, true)) return
                val attributeBuilder = Attributes.builder()
                    .put(AttributeKey.stringKey("http.request.method"), method)
                    .put(AttributeKey.stringKey("server.address"), host)
                    .put(AttributeKey.stringKey("app.http.client.type"), clientType)
                    .put(AttributeKey.stringKey("app.http.outcome"), outcome)
                responseStatusCode?.let {
                    attributeBuilder.put(AttributeKey.longKey("http.response.status_code"), it.toLong())
                }
                val attributes = attributeBuilder.build()
                requestCount.add(1, attributes)
                duration.record((System.nanoTime() - startNanos) / 1_000_000_000.0, attributes)
                if (outcome != "success") errorCount.add(1, attributes)
                request.body?.let { body ->
                    runCatching { body.contentLength() }
                        .getOrNull()
                        ?.takeIf { it >= 0L }
                        ?.let { requestBodySize.record(it.toDouble(), attributes) }
                }
                responseBytesRead?.let { responseBodySize.record(it.toDouble(), attributes) }
                val breadcrumbAttributes = buildMap {
                    put("http.request.method", method)
                    put("app.http.client.type", clientType)
                    put("app.http.outcome", outcome)
                    responseStatusCode?.let { put("http.response.status_code", it.toString()) }
                }
                AppApm.recordBreadcrumb("http.client.request", breadcrumbAttributes)
                span.end()
            }

            try {
                val response = span.makeCurrent().use {
                    val spanContext = span.spanContext
                    val tracedRequest = if (apiOrigin != null && spanContext.isValid) {
                        val traceparent = spanContext.toTraceparent()
                        request.newBuilder()
                            .header("traceparent", traceparent)
                            .tag(InjectedTraceparentTag::class.java, InjectedTraceparentTag(traceparent))
                            .build()
                    } else {
                        request
                    }
                    chain.proceed(tracedRequest)
                }
                val statusCode = response.code
                responseStatusCode = statusCode
                span.setAttribute("http.request.method", method)
                span.setAttribute("server.address", host)
                span.setAttribute("app.http.client.type", clientType)
                span.setAttribute("server.port", request.url.port.toLong())
                span.setAttribute("http.response.status_code", statusCode.toLong())
                outcome = when {
                    statusCode >= 500 -> "server_error"
                    statusCode >= 400 -> "client_error"
                    else -> "success"
                }
                if (statusCode >= 500) span.setStatus(StatusCode.ERROR)
                val originalBody = response.body
                if (originalBody == null) {
                    finishRequest(0L)
                    response
                } else {
                    response.newBuilder()
                        .body(originalBody.withApmCompletion({ bytesRead -> finishRequest(bytesRead) }) { failure ->
                            outcome = "transport_error"
                            span.setAttribute("error.type", failure.javaClass.simpleName)
                            span.setStatus(StatusCode.ERROR)
                        })
                        .build()
                }
            } catch (failure: Throwable) {
                outcome = "transport_error"
                span.setAttribute("http.request.method", method)
                span.setAttribute("server.address", host)
                span.setAttribute("app.http.client.type", clientType)
                span.setAttribute("error.type", failure.javaClass.simpleName)
                span.setStatus(StatusCode.ERROR)
                finishRequest()
                throw failure
            }
        }
    }

    private fun ResponseBody.withApmCompletion(
        complete: (Long) -> Unit,
        onReadFailure: (IOException) -> Unit,
    ): ResponseBody {
        val delegate = this
        return object : ResponseBody() {
            private val bodyBytesRead = AtomicLong(0L)
            private val bufferedSource: BufferedSource by lazy(LazyThreadSafetyMode.NONE) {
                object : ForwardingSource(delegate.source()) {
                    override fun read(sink: Buffer, byteCount: Long): Long = try {
                        super.read(sink, byteCount).also { bytesRead ->
                            if (bytesRead == -1L) complete(bodyBytesRead.get())
                            else if (bytesRead > 0L) bodyBytesRead.addAndGet(bytesRead)
                        }
                    } catch (failure: IOException) {
                        onReadFailure(failure)
                        complete(bodyBytesRead.get())
                        throw failure
                    }

                    override fun close() {
                        try {
                            super.close()
                        } catch (failure: IOException) {
                            onReadFailure(failure)
                            throw failure
                        } finally {
                            complete(bodyBytesRead.get())
                        }
                    }
                }.buffer()
            }

            override fun contentType() = delegate.contentType()

            override fun contentLength() = delegate.contentLength()

            override fun source(): BufferedSource = bufferedSource
        }
    }

    private fun HttpUrl.hasSameOriginAs(other: HttpUrl): Boolean =
        scheme == other.scheme && host == other.host && port == other.port

    private fun io.opentelemetry.api.trace.SpanContext.toTraceparent(): String =
        "00-$traceId-$spanId-${if (traceFlags.isSampled) "01" else "00"}"
}
