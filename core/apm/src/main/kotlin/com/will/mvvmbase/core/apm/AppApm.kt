package com.will.mvvmbase.core.apm

import android.content.Context
import android.graphics.Bitmap
import okhttp3.Call
import okhttp3.Interceptor
import androidx.room.RoomDatabase
import java.util.concurrent.Executor

/** Public entry point for application performance monitoring. */
object AppApm {
    /** Initializes mobile RUM and exporters. An empty endpoint leaves APM disabled. */
    fun initialize(
        context: Context,
        endpoint: String,
        enabled: Boolean = true,
        appVersion: String = "unknown",
        environment: String = "unknown",
    ) = initialize(
        context,
        ApmConfiguration(
            endpoint = endpoint,
            enabled = enabled,
            appVersion = appVersion,
            environment = environment,
        ),
    )

    fun initialize(context: Context, configuration: ApmConfiguration) =
        ApmRuntime.initialize(context, configuration)

    val isInitialized: Boolean
        get() = ApmRuntime.isInitialized

    fun recordException(error: Throwable) = ApmRecorder.recordException(error)

    fun recordEvent(name: String, attributes: Map<String, String> = emptyMap()) =
        ApmRecorder.recordEvent(name, attributes)

    fun recordLog(
        level: ApmLogLevel,
        message: String,
        attributes: Map<String, String> = emptyMap(),
    ) = ApmRecorder.recordLog(level, message, attributes)

    fun recordScreenView(screenName: String) = ApmRecorder.recordScreenView(screenName)

    fun recordBreadcrumb(name: String, attributes: Map<String, String> = emptyMap()) =
        ApmRecorder.recordBreadcrumb(name, attributes)

    fun incrementCounter(name: String, amount: Long = 1, attributes: Map<String, String> = emptyMap()) =
        ApmRecorder.incrementCounter(name, amount, attributes)

    fun recordMeasurement(
        name: String,
        value: Double,
        unit: String = "",
        attributes: Map<String, String> = emptyMap(),
    ) = ApmRecorder.recordMeasurement(name, value, unit, attributes)

    fun <T> trace(name: String, block: () -> T): T = ApmRecorder.trace(name, block)

    fun <T> trace(name: String, attributes: Map<String, String>, block: () -> T): T =
        ApmRecorder.trace(name, attributes, block)

    /** Measures a controlled file/database operation; reports main-thread and slow-I/O diagnostics. */
    fun <T> trackIo(operation: String, resourceType: String = "unknown", block: () -> T): T =
        ApmIoDiagnostics.track(operation, resourceType, block)

    /** Measures file I/O without recording the file path or name. */
    fun <T> trackFileIo(operation: String, fileExtension: String, block: () -> T): T =
        ApmIoDiagnostics.track(operation, fileExtension, block)

    /** Room callback for safe SQL rule checks; SQL text and bind values are never exported. */
    fun roomQueryCallback(): RoomDatabase.QueryCallback = ApmSqlDiagnostics.queryCallback()

    fun roomQueryExecutor(): Executor = ApmSqlDiagnostics.queryExecutor()

    /** Reports identical live bitmap contents; opt in at image decode or repository boundaries. */
    fun inspectBitmap(bitmap: Bitmap, owner: String = "unknown") = ApmBitmapDiagnostics.inspect(bitmap, owner)

    /** Installs Android StrictMode closeable/cursor leak checks. Intended for debug builds. */
    fun installDebugResourceLeakChecks() = ApmResourceLeakDiagnostics.install()

    suspend fun <T> traceSuspend(name: String, block: suspend () -> T): T =
        ApmRecorder.traceSuspend(name, block)

    suspend fun <T> traceSuspend(
        name: String,
        attributes: Map<String, String>,
        block: suspend () -> T,
    ): T = ApmRecorder.traceSuspend(name, attributes, block)

    fun instrumentCallFactory(delegate: Call.Factory, apiBaseUrl: String): Call.Factory =
        ApmNetworkInstrumentation.instrumentCallFactory(delegate, apiBaseUrl)

    fun instrumentCallFactory(delegate: Call.Factory): Call.Factory =
        ApmNetworkInstrumentation.instrumentCallFactory(delegate)

    fun networkInterceptor(apiBaseUrl: String): Interceptor? =
        ApmNetworkInstrumentation.networkInterceptor(apiBaseUrl)

    /** Instruments HTTPS requests made by the credential-free public image loader. */
    fun imageNetworkInterceptor(): Interceptor? = ApmNetworkInstrumentation.imageNetworkInterceptor()

    /** Instruments HTTPS traffic from other explicitly selected credential-safe clients. */
    fun externalHttpsNetworkInterceptor(clientType: String): Interceptor? =
        ApmNetworkInstrumentation.externalHttpsNetworkInterceptor(clientType)

}
