package com.will.mvvmbase.core.apm

import io.opentelemetry.context.Context as OtelContext
import io.opentelemetry.context.Scope
import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Restores OpenTelemetry's thread-local context whenever a coroutine resumes. */
internal class OtelContextElement(
    private val otelContext: OtelContext,
) : ThreadContextElement<Scope>, AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<OtelContextElement>

    override fun updateThreadContext(context: CoroutineContext): Scope = otelContext.makeCurrent()

    override fun restoreThreadContext(context: CoroutineContext, oldState: Scope) {
        oldState.close()
    }
}

internal class CallContextTag(val context: OtelContext)

internal class InjectedTraceparentTag(val value: String)
