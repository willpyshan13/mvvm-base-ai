package com.will.mvvmbase.core.apm

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

/** Low-frequency process/device snapshots sampled only while at least one Activity is visible. */
internal object ApmRuntimeMetrics {
    private val installed = AtomicBoolean(false)
    private val activeActivities = AtomicInteger(0)
    private val previousCpuMillis = AtomicLong(-1)
    private val previousElapsedMillis = AtomicLong(-1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    @Volatile private var applicationContext: Context? = null
    @Volatile private var intervalMillis: Long = 60_000
    private var samplingJob: Job? = null

    fun install(context: Context, interval: Duration) {
        val app = context.applicationContext as? Application ?: return
        if (!installed.compareAndSet(false, true)) return
        applicationContext = app
        intervalMillis = interval.inWholeMilliseconds.coerceAtLeast(15_000)
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit

            override fun onActivityStarted(activity: Activity) {
                if (activeActivities.getAndIncrement() == 0) startSampling()
            }

            override fun onActivityStopped(activity: Activity) {
                val remaining = decrementActiveActivities()
                if (remaining == 0) stopSampling()
            }
        })
    }

    private fun decrementActiveActivities(): Int {
        while (true) {
            val current = activeActivities.get()
            val next = (current - 1).coerceAtLeast(0)
            if (activeActivities.compareAndSet(current, next)) return next
        }
    }

    private fun startSampling() {
        synchronized(lock) {
            if (samplingJob?.isActive == true) return
            previousCpuMillis.set(-1)
            previousElapsedMillis.set(-1)
            samplingJob = scope.launch {
                sample()
                while (isActive) {
                    delay(intervalMillis)
                    sample()
                }
            }
        }
    }

    private fun stopSampling() {
        synchronized(lock) {
            samplingJob?.cancel()
            samplingJob = null
            previousCpuMillis.set(-1)
            previousElapsedMillis.set(-1)
        }
    }

    private fun sample() {
        val context = applicationContext ?: return
        runCatching {
            val memoryInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memoryInfo)
            AppApm.recordMeasurement("app.runtime.memory.pss", memoryInfo.totalPss * 1024.0, "By")

            val runtime = Runtime.getRuntime()
            val heapUsed = runtime.totalMemory() - runtime.freeMemory()
            AppApm.recordMeasurement("app.runtime.memory.heap.used", heapUsed.toDouble(), "By")
            AppApm.recordMeasurement("app.runtime.memory.native.used", Debug.getNativeHeapAllocatedSize().toDouble(), "By")
            val threadCount = runCatching { java.io.File("/proc/self/task").list()?.size }.getOrNull()
            if (threadCount != null) {
                AppApm.recordMeasurement("app.runtime.thread.count", threadCount.toDouble(), "{thread}")
            }

            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager != null) {
                val deviceMemory = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(deviceMemory)
                AppApm.recordMeasurement("app.runtime.memory.device.available", deviceMemory.availMem.toDouble(), "By")
                AppApm.recordMeasurement("app.runtime.memory.device.total", deviceMemory.totalMem.toDouble(), "By")
                AppApm.recordMeasurement("app.runtime.memory.device.low", if (deviceMemory.lowMemory) 1.0 else 0.0, "1")
            }

            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val batteryPercent = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (batteryPercent != null && batteryPercent in 0..100) {
                AppApm.recordMeasurement("app.runtime.battery.level", batteryPercent.toDouble(), "%")
            }
            val batteryStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            } else null
            if (batteryStatus != null && batteryStatus != Integer.MIN_VALUE) {
                AppApm.recordMeasurement("app.runtime.battery.status", batteryStatus.toDouble(), "1")
            }

            val storage = StatFs(context.filesDir.absolutePath)
            val availableStorage = storage.availableBytes
            AppApm.recordMeasurement("app.runtime.storage.available", availableStorage.toDouble(), "By")
            AppApm.recordMeasurement("app.runtime.storage.total", storage.totalBytes.toDouble(), "By")

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null) {
                AppApm.recordMeasurement("app.runtime.power_saver.enabled", if (powerManager.isPowerSaveMode) 1.0 else 0.0, "1")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AppApm.recordMeasurement("app.runtime.thermal.status", powerManager.currentThermalStatus.toDouble(), "1")
                }
            }
            recordCpuUtilization()
        }
    }

    private fun recordCpuUtilization() {
        val cpuMillis = Process.getElapsedCpuTime()
        val elapsedMillis = SystemClock.elapsedRealtime()
        val oldCpu = previousCpuMillis.getAndSet(cpuMillis)
        val oldElapsed = previousElapsedMillis.getAndSet(elapsedMillis)
        if (oldCpu < 0 || oldElapsed < 0) return
        val elapsedDelta = elapsedMillis - oldElapsed
        if (elapsedDelta <= 0) return
        val cpuDelta = (cpuMillis - oldCpu).coerceAtLeast(0)
        val cpuRatio = (cpuDelta.toDouble() / elapsedDelta / Runtime.getRuntime().availableProcessors())
            .coerceIn(0.0, 1.0)
        AppApm.recordMeasurement("app.runtime.cpu.utilization", cpuRatio, "1")
    }
}
