package com.will.mvvmbase.core.apm

/** Opts a concrete JVM method into build-time slow-method timing instrumentation. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ApmMonitoredMethod(val slowThresholdMillis: Long = 16)
