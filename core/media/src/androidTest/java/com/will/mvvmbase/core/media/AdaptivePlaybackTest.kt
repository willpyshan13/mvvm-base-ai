package com.will.mvvmbase.core.media

import android.content.Context
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
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AdaptivePlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    @Test fun hlsDemuxesAudioAndRendersVideo() = play("master.m3u8")
    @Test fun dashDemuxesAudioAndRendersVideo() = play("manifest.mpd")

    private fun play(manifest: String) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val assets = instrumentation.context.assets
        val files = assets.list("streams")!!.associateWith { assets.open("streams/$it").use { input -> input.readBytes() } }
        AssetOrigin(files).use { origin ->
            lateinit var controller: AppVideoPlayerController
            var reader: ImageReader? = null
            val frames = AtomicInteger()
            val consumer = HandlerThread("adaptive-frame-consumer").apply { start() }
            try {
                main {
                    controller = AppVideoPlayerController(context, 10_000, 10_000)
                    controller.setMediaSource(Uri.parse(origin.url(manifest)), false)
                }
                waitFor { controller.state.value.playbackState == AppVideoPlaybackState.Ready || controller.state.value.error != null }
                assertNull("$manifest prepare: ${controller.state.value.error}", controller.state.value.error)
                main {
                    reader = ImageReader.newInstance(controller.state.value.videoWidth,
                        controller.state.value.videoHeight, PixelFormat.RGBA_8888, 3).apply {
                        setOnImageAvailableListener({ source -> source.acquireLatestImage()?.use { frames.incrementAndGet() } },
                            Handler(consumer.looper))
                    }
                    controller.setSurface(reader!!.surface)
                    controller.play()
                }
                waitFor { frames.get() >= 8 || controller.state.value.error != null }
                assertNull("$manifest decode: ${controller.state.value.error}", controller.state.value.error)
                assertTrue("Video was not rendered", frames.get() >= 8)
                assertTrue("Audio segments not requested", origin.requested.any { it.contains("stream1") })
                assertTrue("Video segments not requested", origin.requested.any { it.contains("stream0") })
            } finally {
                main { controller.release() }
                SystemClock.sleep(200)
                reader?.close()
                consumer.quitSafely()
            }
        }
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Timed out waiting for FFmpeg", condition())
    }

    private class AssetOrigin(private val files: Map<String, ByteArray>) : Closeable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val workers = Executors.newFixedThreadPool(4)
        val requested: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
        fun url(path: String) = "http://127.0.0.1:${server.localPort}/$path"
        init {
            Thread({
                while (!server.isClosed) {
                    try {
                        val socket = server.accept()
                        workers.execute {
                            socket.use {
                                val reader = it.getInputStream().bufferedReader()
                                val path = reader.readLine()?.split(' ')?.getOrNull(1)?.substringBefore('?')?.trimStart('/') ?: return@use
                                var range: String? = null
                                while (true) {
                                    val line = reader.readLine() ?: return@use
                                    if (line.isEmpty()) break
                                    if (line.startsWith("Range:", true)) range = line.substringAfter(':').trim()
                                }
                                requested.add(path)
                                val body = files[path] ?: return@use
                                val match = range?.let { value -> Regex("bytes=(\\d+)-(\\d*)").matchEntire(value) }
                                val start = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                val last = (match?.groupValues?.get(2)?.toIntOrNull() ?: (body.size - 1)).coerceAtMost(body.size - 1)
                                val type = when {
                                    path.endsWith(".mpd") -> "application/dash+xml"
                                    path.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
                                    else -> "video/mp4"
                                }
                                val header = buildString {
                                    append("HTTP/1.1 ${if (match == null) 200 else 206} OK\r\n")
                                    append("Content-Type: $type\r\nContent-Length: ${last - start + 1}\r\n")
                                    if (match != null) append("Content-Range: bytes $start-$last/${body.size}\r\n")
                                    append("Cache-Control: public, max-age=60\r\nConnection: close\r\n\r\n")
                                }
                                it.getOutputStream().apply {
                                    write(header.toByteArray())
                                    write(body, start, last - start + 1)
                                    flush()
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }
            }, "adaptive-origin").apply { isDaemon = true; start() }
        }
        override fun close() { server.close(); workers.shutdownNow() }
    }
}
