package com.will.mvvmbase.core.platform

import android.content.Context
import android.os.SystemClock
import java.io.File

/** Local startup and last-crash diagnostics. Remote telemetry lives in the separate core:apm module. */
object AppDiagnostics {
    private val startedAt = SystemClock.elapsedRealtime()
    @Volatile var startupMs: Long? = null
        private set
    fun firstFrame() { if (startupMs == null) startupMs = SystemClock.elapsedRealtime() - startedAt }
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                // No exception messages, stack traces, tokens, or personal data are persisted.
                File(context.filesDir, "last-crash.txt").writeText("${System.currentTimeMillis()} ${error.javaClass.simpleName}")
            } catch (_: Exception) { }
            finally { previous?.uncaughtException(thread, error) }
        }
    }
    fun lastCrash(context: Context, emptyMessage: String): String = runCatching {
        File(context.filesDir, "last-crash.txt").readText()
    }.getOrDefault(emptyMessage)
}
