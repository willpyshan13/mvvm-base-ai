package com.will.mvvmbase.core.apm

import android.content.Context
import android.util.Log
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.agent.OpenTelemetryRumInitializer
import java.util.concurrent.atomic.AtomicReference
/** Owns the single process-wide OpenTelemetry Android runtime. */
internal object ApmRuntime {
    private const val TAG = "AppApm"
    private const val INSTRUMENTATION_SCOPE = "com.will.mvvmbase"

    private val rum = AtomicReference<OpenTelemetryRum?>(null)
    private val initLock = Any()

    val isInitialized: Boolean
        get() = rum.get() != null

    fun openTelemetry(): OpenTelemetry? = rum.get()?.openTelemetry

    fun tracer(): Tracer? = openTelemetry()?.getTracer(INSTRUMENTATION_SCOPE)

    /** An empty endpoint disables remote collection. Only HTTPS collectors are accepted. */
    fun initialize(
        context: Context,
        configuration: ApmConfiguration,
    ) {
        if (!configuration.enabled) return
        val normalizedEndpoint = configuration.endpoint.trim()
        if (normalizedEndpoint.isEmpty()) return
        if (!normalizedEndpoint.startsWith("https://", ignoreCase = true)) {
            Log.w(TAG, "OpenTelemetry initialization skipped: endpoint must use HTTPS")
            return
        }
        if (configuration.sessionInactivityTimeout <= kotlin.time.Duration.ZERO ||
            configuration.sessionMaxLifetime <= kotlin.time.Duration.ZERO ||
            configuration.runtimeMetricsInterval <= kotlin.time.Duration.ZERO ||
            configuration.anrTimeout <= kotlin.time.Duration.ZERO ||
            configuration.slowIoThreshold <= kotlin.time.Duration.ZERO
        ) {
            Log.w(TAG, "OpenTelemetry initialization skipped: durations must be positive")
            return
        }
        if (rum.get() != null) return

        synchronized(initLock) {
            if (rum.get() != null) return
            try {
                val instance = OpenTelemetryRumInitializer.initialize(context.applicationContext) {
                    httpExport {
                        baseUrl = normalizedEndpoint
                        baseHeaders = configuration.exportHeaders.toMap()
                    }
                    diskBuffering {
                        enabled(true)
                    }
                    session {
                        backgroundInactivityTimeout = configuration.sessionInactivityTimeout
                        maxLifetime = configuration.sessionMaxLifetime
                    }
                    globalAttributes {
                        Attributes.of(
                            AttributeKey.stringKey("deployment.environment.name"), configuration.environment,
                            AttributeKey.stringKey("app.version"), configuration.appVersion,
                        )
                    }
                    // Bundled lifecycle, crash, ANR, startup, network-state, rendering,
                    // orientation and session instrumentations use their SDK defaults.
                }
                rum.set(instance)
                if (configuration.runtimeMetricsEnabled) {
                    runCatching { ApmRuntimeMetrics.install(context, configuration.runtimeMetricsInterval) }
                        .onFailure { failure -> Log.w(TAG, "Runtime metrics initialization failed (${failure.javaClass.simpleName})") }
                }
                runCatching {
                    ApmIoDiagnostics.configure(configuration.slowIoThreshold.inWholeMilliseconds)
                    ApmDiagnosticMonitors.install(context.applicationContext as android.app.Application, configuration)
                }.onFailure { failure ->
                    Log.w(TAG, "Diagnostic monitors initialization failed (${failure.javaClass.simpleName})")
                }
            } catch (failure: Exception) {
                // Monitoring setup must never prevent the app from starting.
                Log.w(TAG, "OpenTelemetry initialization failed (${failure.javaClass.simpleName})")
            }
        }
    }
}
