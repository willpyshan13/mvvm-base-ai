package com.will.mvvmbase.core.apm

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

class OtelContextElementTest {
    @Test
    fun traceContextSurvivesDispatcherChangesAndRestoresCallerContext() = runBlocking {
        val original = Span.current().spanContext
        val expected = SpanContext.create(
            "12345678901234567890123456789012",
            "1234567890123456",
            TraceFlags.getSampled(),
            TraceState.getDefault(),
        )
        val context = Context.current().with(Span.wrap(expected))
        withContext(OtelContextElement(context)) {
            assertEquals(expected, Span.current().spanContext)
            withContext(Dispatchers.IO) { assertEquals(expected, Span.current().spanContext) }
            withContext(Dispatchers.Default) { assertEquals(expected, Span.current().spanContext) }
        }
        assertEquals(original, Span.current().spanContext)
    }
}
