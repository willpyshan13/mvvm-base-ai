package com.will.mvvmbase.core.apm

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Configuration for the mobile APM client. Supply short-lived export credentials at initialization. */
data class ApmConfiguration(
    val endpoint: String,
    val enabled: Boolean = true,
    val appVersion: String = "unknown",
    val environment: String = "unknown",
    val exportHeaders: Map<String, String> = emptyMap(),
    val sessionInactivityTimeout: Duration = 15.minutes,
    val sessionMaxLifetime: Duration = 4.days,
    val runtimeMetricsEnabled: Boolean = true,
    val runtimeMetricsInterval: Duration = 60.seconds,
    val frameMetricsEnabled: Boolean = true,
    val anrWatchdogEnabled: Boolean = true,
    val anrTimeout: Duration = 5.seconds,
    val slowIoThreshold: Duration = 200.milliseconds,
)
