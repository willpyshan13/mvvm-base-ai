package com.will.mvvmbase.core.apm

import java.util.ArrayDeque

/** Stable JVM entry points called by the AGP bytecode instrumentation visitor. */
object ApmMethodTraceBridge {
    private data class Invocation(val method: String, val startedAtNanos: Long)

    private val invocations = ThreadLocal<ArrayDeque<Invocation>>()

    @JvmStatic
    fun begin(method: String) {
        if (!AppApm.isInitialized) return
        val stack = invocations.get() ?: ArrayDeque<Invocation>().also(invocations::set)
        stack.addLast(Invocation(method, System.nanoTime()))
    }

    @JvmStatic
    fun end(method: String, slowThresholdMillis: Long) {
        finish(method, slowThresholdMillis, null)
    }

    @JvmStatic
    fun fail(error: Throwable, method: String, slowThresholdMillis: Long) {
        finish(method, slowThresholdMillis, error)
    }

    private fun finish(method: String, thresholdMillis: Long, error: Throwable?) {
        if (!AppApm.isInitialized) return
        val stack = invocations.get() ?: return
        val invocation = if (stack.peekLast()?.method == method) stack.removeLast() else {
            val matching = stack.toList().asReversed().firstOrNull { it.method == method } ?: return
            while (stack.isNotEmpty()) {
                val current = stack.removeLast()
                if (current === matching) break
            }
            matching
        }
        if (stack.isEmpty()) invocations.remove()
        val durationNanos = (System.nanoTime() - invocation.startedAtNanos).coerceAtLeast(0L)
        val durationMillis = durationNanos / 1_000_000.0
        val attributes = mapOf("method" to method)
        AppApm.recordMeasurement("app.method.duration", durationNanos / 1_000_000_000.0, "s", attributes)
        if (durationNanos >= thresholdMillis.coerceAtLeast(0L) * 1_000_000L) {
            AppApm.recordEvent("app.method.slow", attributes + ("duration_ms" to durationMillis.toLong().toString()))
        }
        if (error != null) {
            AppApm.recordException(error)
            AppApm.recordEvent(
                "app.method.failure",
                attributes + ("exception.type" to error.javaClass.name),
            )
        }
    }
}
