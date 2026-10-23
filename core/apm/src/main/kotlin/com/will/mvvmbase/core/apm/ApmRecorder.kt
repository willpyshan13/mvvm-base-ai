package com.will.mvvmbase.core.apm

import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context as OtelContext
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Manual business instrumentation kept separate from SDK initialization and network hooks. */
internal object ApmRecorder {
    private const val INSTRUMENTATION_SCOPE = "com.will.mvvmbase"
    private const val MAX_BREADCRUMBS = 50
    private const val MAX_INSTRUMENTS_PER_KIND = 128

    private val counters = ConcurrentHashMap<String, LongCounter>()
    private val histograms = ConcurrentHashMap<String, HistogramInstrument>()
    private val breadcrumbLock = Any()
    private val breadcrumbs = ArrayDeque<Breadcrumb>()

    fun recordException(error: Throwable) {
        val activeTracer = ApmRuntime.tracer() ?: return
        val span = activeTracer.spanBuilder("app.handled_exception").startSpan()
        try {
            span.recordException(error)
            synchronized(breadcrumbLock) {
                breadcrumbs.forEach { (name, attributes, timestampMillis) ->
                    span.addEvent(name, attributes, timestampMillis, TimeUnit.MILLISECONDS)
                }
            }
            span.setStatus(StatusCode.ERROR)
        } finally {
            span.end()
        }
    }

    fun recordEvent(name: String, attributes: Map<String, String>) {
        if (!ApmPrivacy.isSafeName(name)) return
        val openTelemetry = ApmRuntime.openTelemetry() ?: return
        openTelemetry.getLogsBridge().get(INSTRUMENTATION_SCOPE)
            .logRecordBuilder()
            .setTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setContext(OtelContext.current())
            .setEventName(name)
            .setSeverity(Severity.INFO)
            .setAllAttributes(ApmPrivacy.attributes(attributes))
            .emit()
    }

    fun recordLog(level: ApmLogLevel, message: String, attributes: Map<String, String>) {
        if (message.isBlank()) return
        val openTelemetry = ApmRuntime.openTelemetry() ?: return
        val severity = when (level) {
            ApmLogLevel.DEBUG -> Severity.DEBUG
            ApmLogLevel.INFO -> Severity.INFO
            ApmLogLevel.WARN -> Severity.WARN
            ApmLogLevel.ERROR -> Severity.ERROR
        }
        openTelemetry.getLogsBridge().get(INSTRUMENTATION_SCOPE)
            .logRecordBuilder()
            .setTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setContext(OtelContext.current())
            .setSeverity(severity)
            .setBody(ApmPrivacy.safeLogMessage(message))
            .setAllAttributes(ApmPrivacy.attributes(attributes))
            .emit()
    }

    fun recordScreenView(screenName: String) {
        recordBreadcrumb("app.screen.view", mapOf("screen.name" to screenName))
        recordEvent("app.screen.view", mapOf("screen.name" to screenName))
    }

    /** Keeps a small, in-memory trail and attaches it to manually reported exceptions. */
    fun recordBreadcrumb(name: String, attributes: Map<String, String>) {
        if (!ApmRuntime.isInitialized) return
        if (!ApmPrivacy.isSafeName(name)) return
        val breadcrumb = Breadcrumb(name, ApmPrivacy.attributes(attributes), System.currentTimeMillis())
        synchronized(breadcrumbLock) {
            if (breadcrumbs.size == MAX_BREADCRUMBS) breadcrumbs.removeFirst()
            breadcrumbs.addLast(breadcrumb)
        }
    }

    fun incrementCounter(name: String, amount: Long, attributes: Map<String, String>) {
        if (!name.startsWith("app.") || !ApmPrivacy.isSafeName(name) || amount <= 0) return
        val meter = ApmRuntime.openTelemetry()?.getMeter(INSTRUMENTATION_SCOPE) ?: return
        val counter = synchronized(counters) {
            counters[name] ?: if (counters.size < MAX_INSTRUMENTS_PER_KIND) {
                meter.counterBuilder(name).build().also { counters[name] = it }
            } else null
        } ?: return
        counter.add(amount, ApmPrivacy.attributes(attributes))
    }

    /** Records a numeric sample in a histogram, suitable for custom duration/size measurements. */
    fun recordMeasurement(name: String, value: Double, unit: String, attributes: Map<String, String>) {
        if (!name.startsWith("app.") || !ApmPrivacy.isSafeName(name) || !value.isFinite()) return
        if (unit.length > 24 || unit.any {
                !(it.isLetterOrDigit() || it == '{' || it == '}' || it == '/' || it == '%' || it == '-' || it == '^' || it == '*')
            }
        ) return
        val meter = ApmRuntime.openTelemetry()?.getMeter(INSTRUMENTATION_SCOPE) ?: return
        val histogram = synchronized(histograms) {
            histograms[name] ?: if (histograms.size < MAX_INSTRUMENTS_PER_KIND) {
                HistogramInstrument(unit, meter.histogramBuilder(name).setUnit(unit).build())
                    .also { histograms[name] = it }
            } else null
        } ?: return
        if (histogram.unit != unit) return
        histogram.histogram.record(value, ApmPrivacy.attributes(attributes))
    }

    fun <T> trace(name: String, block: () -> T): T {
        return trace(name, emptyMap(), block)
    }

    fun <T> trace(name: String, attributes: Map<String, String>, block: () -> T): T {
        val activeTracer = ApmRuntime.tracer()
        if (activeTracer == null || !ApmPrivacy.isSafeName(name)) return block()
        val span = activeTracer.spanBuilder(name)
            .startSpan()
        span.setAllAttributes(ApmPrivacy.attributes(attributes))
        return try {
            span.makeCurrent().use { block() }
        } catch (error: Throwable) {
            if (error !is CancellationException) {
                span.recordException(error)
                span.setStatus(StatusCode.ERROR)
            }
            throw error
        } finally {
            span.end()
        }
    }

    suspend fun <T> traceSuspend(name: String, block: suspend () -> T): T {
        return traceSuspend(name, emptyMap(), block)
    }

    suspend fun <T> traceSuspend(
        name: String,
        attributes: Map<String, String>,
        block: suspend () -> T,
    ): T {
        val activeTracer = ApmRuntime.tracer()
        if (activeTracer == null || !ApmPrivacy.isSafeName(name)) return block()
        val span = activeTracer.spanBuilder(name).startSpan()
        span.setAllAttributes(ApmPrivacy.attributes(attributes))
        return try {
            val context = OtelContext.current().with(span)
            withContext(OtelContextElement(context)) { block() }
        } catch (error: Throwable) {
            if (error !is CancellationException) {
                span.recordException(error)
                span.setStatus(StatusCode.ERROR)
            }
            throw error
        } finally {
            span.end()
        }
    }

    private data class Breadcrumb(
        val name: String,
        val attributes: io.opentelemetry.api.common.Attributes,
        val timestampMillis: Long,
    )

    private data class HistogramInstrument(
        val unit: String,
        val histogram: DoubleHistogram,
    )
}
