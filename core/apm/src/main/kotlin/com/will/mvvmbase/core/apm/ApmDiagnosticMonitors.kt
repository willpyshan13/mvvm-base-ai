package com.will.mvvmbase.core.apm

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Runtime diagnostics that can be collected without bytecode instrumentation. */
internal object ApmDiagnosticMonitors {
    private val installed = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainHeartbeat = AtomicLong(SystemClock.uptimeMillis())
    private val lastAnrReport = AtomicLong(0L)
    private val processStartElapsedMillis = AtomicLong(0L)
    private val firstDrawRecorded = AtomicBoolean(false)
    private val watchdog = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "app-apm-anr-watchdog").apply { isDaemon = true }
    }

    fun install(application: Application, configuration: ApmConfiguration) {
        if (!installed.compareAndSet(false, true)) return
        processStartElapsedMillis.set(SystemClock.elapsedRealtime())
        if (configuration.anrWatchdogEnabled) {
            val timeout = configuration.anrTimeout.inWholeMilliseconds.coerceAtLeast(1_000)
            val tick = (timeout / 2).coerceAtLeast(500)
            val pulse = object : Runnable {
                override fun run() {
                    mainHeartbeat.set(SystemClock.uptimeMillis())
                    mainHandler.postDelayed(this, tick)
                }
            }
            mainHandler.post(pulse)
            watchdog.scheduleAtFixedRate({
                val stalledFor = SystemClock.uptimeMillis() - mainHeartbeat.get()
                if (stalledFor >= timeout) {
                    val thread = Looper.getMainLooper().thread
                    val now = SystemClock.uptimeMillis()
                    val lastReported = lastAnrReport.get()
                    if (now - lastReported >= timeout && lastAnrReport.compareAndSet(lastReported, now)) {
                        AppApm.recordException(AnrSuspectedException(stalledFor, thread.stackTrace))
                    }
                }
            }, tick, tick, TimeUnit.MILLISECONDS)
        }

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private val createdAt = java.util.WeakHashMap<Activity, Long>()
            private val activeFrames = java.util.WeakHashMap<Activity, FrameSampler>()
            private val firstDrawListeners = java.util.WeakHashMap<Activity, ViewTreeObserver.OnPreDrawListener>()

            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {
                createdAt[activity] = SystemClock.elapsedRealtime()
                if (configuration.frameMetricsEnabled) {
                    val activityReference = java.lang.ref.WeakReference(activity)
                    val listener = object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            val currentActivity = activityReference.get() ?: return true
                            val view = currentActivity.window.decorView
                            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnPreDrawListener(this)
                            firstDrawListeners.remove(currentActivity)
                            val elapsed = SystemClock.elapsedRealtime() - (createdAt[currentActivity] ?: return true)
                            val attributes = mapOf("activity" to currentActivity.javaClass.simpleName)
                            AppApm.recordMeasurement("app.startup.activity.first_draw", elapsed / 1000.0, "s", attributes)
                            if (firstDrawRecorded.compareAndSet(false, true)) {
                                val processElapsed = SystemClock.elapsedRealtime() - processStartElapsedMillis.get()
                                AppApm.recordMeasurement("app.startup.process.first_draw", processElapsed / 1000.0, "s")
                            }
                            return true
                        }
                    }
                    firstDrawListeners[activity] = listener
                    val view: View = activity.window.decorView
                    view.viewTreeObserver.addOnPreDrawListener(listener)
                }
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) {
                if (configuration.frameMetricsEnabled && activeFrames[activity] == null) {
                    val refreshRate = displayRefreshRate(activity)
                    FrameSampler(activity.javaClass.simpleName, (1_000_000_000.0 / refreshRate).toLong()).also {
                        activeFrames[activity] = it
                        it.start()
                    }
                }
            }

            override fun onActivityPaused(activity: Activity) {
                activeFrames.remove(activity)?.stop()
            }

            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                createdAt.remove(activity)
                firstDrawListeners.remove(activity)?.let { listener ->
                    activity.window.decorView.viewTreeObserver.takeIf { it.isAlive }
                        ?.removeOnPreDrawListener(listener)
                }
                activeFrames.remove(activity)?.stop()
            }
        })
    }

    @Suppress("DEPRECATION")
    private fun displayRefreshRate(activity: Activity): Float {
        val rate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.refreshRate
        } else {
            activity.windowManager.defaultDisplay.refreshRate
        }
        return (rate ?: 60f).coerceAtLeast(1f)
    }

    private class FrameSampler(
        private val activityName: String,
        private val frameBudgetNanos: Long,
    ) : Choreographer.FrameCallback {
        private var lastFrameNanos = 0L
        private var frames = 0L
        private var slowFrames = 0L
        private var frozenFrames = 0L
        private var totalFrameNanos = 0L
        private var windowStart = SystemClock.elapsedRealtime()
        private var stopped = false

        fun start() = Choreographer.getInstance().postFrameCallback(this)
        fun stop() {
            stopped = true
            Choreographer.getInstance().removeFrameCallback(this)
            flush()
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (stopped) return
            if (lastFrameNanos != 0L) {
                val delta = (frameTimeNanos - lastFrameNanos).coerceAtLeast(0L)
                frames++
                totalFrameNanos += delta
                if (delta > frameBudgetNanos) slowFrames++
                if (delta > 700_000_000L) frozenFrames++
            }
            lastFrameNanos = frameTimeNanos
            if (SystemClock.elapsedRealtime() - windowStart >= 10_000) flush()
            Choreographer.getInstance().postFrameCallback(this)
        }

        private fun flush() {
            if (frames == 0L) return
            val attrs = mapOf("activity" to activityName)
            AppApm.recordMeasurement("app.render.frames", frames.toDouble(), "{frame}", attrs)
            val windowDurationMillis = (SystemClock.elapsedRealtime() - windowStart).coerceAtLeast(1L)
            AppApm.recordMeasurement("app.render.fps", frames * 1_000.0 / windowDurationMillis, "{frame}/s", attrs)
            AppApm.recordMeasurement("app.render.frame_duration.average", totalFrameNanos / frames / 1_000_000.0, "ms", attrs)
            AppApm.recordMeasurement("app.render.slow_frames", slowFrames.toDouble(), "{frame}", attrs)
            AppApm.recordMeasurement("app.render.frozen_frames", frozenFrames.toDouble(), "{frame}", attrs)
            if (slowFrames > 0) AppApm.recordEvent("app.render.jank_window", attrs + ("slow_frames" to slowFrames.toString()))
            if (frozenFrames > 0) AppApm.recordEvent("app.render.frozen_frame", attrs + ("count" to frozenFrames.toString()))
            frames = 0
            slowFrames = 0
            frozenFrames = 0
            totalFrameNanos = 0
            windowStart = SystemClock.elapsedRealtime()
        }
    }

    private class AnrSuspectedException(stallMillis: Long, mainThreadStack: Array<StackTraceElement>) :
        RuntimeException("Main thread unresponsive for ${stallMillis}ms") {
        init {
            stackTrace = mainThreadStack
        }
    }
}

internal object ApmIoDiagnostics {
    @Volatile private var slowIoThresholdNanos: Long = 200_000_000L

    fun configure(slowIoThresholdMillis: Long) {
        slowIoThresholdNanos = slowIoThresholdMillis.coerceAtLeast(1L) * 1_000_000L
    }

    fun <T> track(operation: String, resourceType: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtimeNanos()
        val mainThread = Looper.myLooper() == Looper.getMainLooper()
        return try {
            block()
        } catch (failure: Throwable) {
            if (failure !is kotlinx.coroutines.CancellationException) AppApm.recordException(failure)
            throw failure
        } finally {
            val elapsed = (SystemClock.elapsedRealtimeNanos() - start).coerceAtLeast(0L)
            val attributes = mapOf(
                "io.operation" to operation,
                "io.resource.type" to resourceType.ifBlank { "unknown" },
                "thread.main" to mainThread.toString(),
            )
            AppApm.recordMeasurement("app.io.duration", elapsed / 1_000_000_000.0, "s", attributes)
            if (mainThread) AppApm.incrementCounter("app.io.main_thread.count", attributes = attributes)
            if (elapsed >= slowIoThresholdNanos) AppApm.recordEvent("app.io.slow", attributes)
        }
    }
}
