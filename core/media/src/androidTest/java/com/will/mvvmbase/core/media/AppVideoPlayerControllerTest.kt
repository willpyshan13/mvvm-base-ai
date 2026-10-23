package com.will.mvvmbase.core.media

import android.graphics.PixelFormat
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AppVideoPlayerControllerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue(message, condition())
    }

    @Test fun nativeDecoderRendersSeeksChangesSpeedLoopsAndReleasesSafely() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "ffmpeg-test.mp4")
        instrumentation.context.assets.open("flower.mp4").use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        lateinit var controller: AppVideoPlayerController
        var reader: ImageReader? = null
        val frames = AtomicInteger()
        val consumer = HandlerThread("test-video-consumer").apply { start() }
        try {
            main {
                controller = AppVideoPlayerController(context, 10_000, 10_000)
                controller.setMediaSource(Uri.fromFile(file), false, 1_000)
            }
            waitFor("Native prepare failed: ${controller.state.value.error}") {
                controller.state.value.playbackState == AppVideoPlaybackState.Ready || controller.state.value.error != null
            }
            assertNull(controller.state.value.error)
            assertTrue(controller.state.value.durationMs > 2_000)
            assertTrue(controller.state.value.videoWidth > 0)
            main {
                reader = ImageReader.newInstance(controller.state.value.videoWidth,
                    controller.state.value.videoHeight, PixelFormat.RGBA_8888, 3).apply {
                    setOnImageAvailableListener({ imageReader ->
                        imageReader.acquireLatestImage()?.use { frames.incrementAndGet() }
                    }, Handler(consumer.looper))
                }
                controller.setSurface(reader!!.surface)
                controller.setPlaybackSpeed(2f)
                controller.setRepeatMode(AppVideoRepeatMode.One)
                controller.play()
            }
            waitFor("FFmpeg did not render video frames") { frames.get() >= 5 || controller.state.value.error != null }
            assertNull(controller.state.value.error)
            assertTrue(frames.get() >= 5)
            main { controller.updateState(); controller.pause() }
            val paused = controller.state.value.positionMs
            SystemClock.sleep(250)
            main { controller.updateState() }
            assertTrue(kotlin.math.abs(controller.state.value.positionMs - paused) < 200)
            main { controller.seekBy(-Long.MAX_VALUE) }
            assertEquals(0, controller.state.value.positionMs)
            val before = frames.get()
            main { controller.play() }
            waitFor("Seek did not resume rendering") { frames.get() >= before + 5 }
            // The sample is five seconds long; two-speed playback must survive at least one loop.
            SystemClock.sleep(4_000)
            main { controller.updateState() }
            assertNull(controller.state.value.error)
            assertEquals(AppVideoPlaybackState.Ready, controller.state.value.playbackState)
            assertTrue(frames.get() > before + 30)
            assertEquals(2f, controller.state.value.playbackSpeed)
            assertEquals(AppVideoRepeatMode.One, controller.state.value.repeatMode)
        } finally {
            main { controller.release(); controller.release() }
            SystemClock.sleep(200)
            reader?.close()
            consumer.quitSafely()
            file.delete()
        }
    }

    @Test fun unavailableSourceReportsNativeErrorAndAllowsRelease() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        lateinit var controller: AppVideoPlayerController
        main {
            controller = AppVideoPlayerController(context, 10_000, 10_000)
            controller.setMediaSource(Uri.fromFile(File(context.cacheDir, "missing-video.mp4")), false)
        }
        try {
            waitFor("Expected a native source error") { controller.state.value.error != null }
            assertFalse(controller.state.value.isPlaying)
        } finally { main { controller.release(); controller.release() } }
    }
}
