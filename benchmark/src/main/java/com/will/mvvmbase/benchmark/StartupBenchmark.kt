package com.will.mvvmbase.benchmark

import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()
    @Test fun scrollExamples() = benchmark.measureRepeated(
        packageName = BuildConfig.TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()), compilationMode = CompilationMode.None(), iterations = 3,
        setupBlock = {
            startActivityAndWait(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("wbase://open/toolbox")).setPackage(BuildConfig.TARGET_PACKAGE))
        },
    ) {
        val x = device.displayWidth / 2
        val top = device.displayHeight / 4
        val bottom = device.displayHeight * 3 / 4
        device.swipe(x, bottom, x, top, 20)
        device.waitForIdle()
        device.swipe(x, top, x, bottom, 20)
    }
    @Test fun coldStartup() = benchmark.measureRepeated(
        packageName = BuildConfig.TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.None(), iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
    ) { startActivityAndWait(); device.waitForIdle() }
}
