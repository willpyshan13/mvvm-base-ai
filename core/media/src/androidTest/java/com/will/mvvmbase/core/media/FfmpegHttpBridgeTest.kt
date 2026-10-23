package com.will.mvvmbase.core.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class FfmpegHttpBridgeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun cachesCompleteResponseAndServesByteRangeWithoutNetwork() {
        AppVideoCache.configure(context, 32L * 1024 * 1024)
        val body = ByteArray(8192) { (it % 251).toByte() }
        val origin = Origin(body, "video/mp4")
        val bridge = FfmpegHttpBridge(context)
        try {
            val url = bridge.proxyUrl(origin.url("movie.mp4"))
            val initial = URL(url).openConnection() as HttpURLConnection
            assertArrayEquals(body, initial.inputStream.use { it.readBytes() })
            initial.disconnect()
            origin.close()
            val cached = URL(url).openConnection() as HttpURLConnection
            cached.setRequestProperty("Range", "bytes=100-299")
            assertEquals(206, cached.responseCode)
            assertEquals("bytes 100-299/8192", cached.getHeaderField("Content-Range"))
            assertArrayEquals(body.copyOfRange(100, 300), cached.inputStream.use { it.readBytes() })
            cached.disconnect()
            assertEquals(1, origin.requests.get())
        } finally { bridge.close(); origin.close() }
    }

    @Test fun hlsPlaylistResolvesSegmentsAndKeysThroughTheSameBridge() {
        val text = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"../keys/main.key\"\n#EXTINF:2,\nsegment.ts\n"
        val origin = Origin(text.toByteArray(), "application/vnd.apple.mpegurl")
        val bridge = FfmpegHttpBridge(context)
        try {
            val playlist = URL(bridge.proxyUrl(origin.url("video/list.m3u8"))).readText()
            assertTrue(playlist.contains(bridge.proxyUrl(origin.url("keys/main.key"))))
            assertTrue(playlist.contains(bridge.proxyUrl(origin.url("video/segment.ts"))))
        } finally { bridge.close(); origin.close() }
    }

    @Test fun dashManifestKeepsTemplatesAndBridgesAbsoluteBaseUrls() {
        val origin = Origin(ByteArray(0), "application/dash+xml")
        origin.body = """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011"><Period><AdaptationSet><Representation id="v"><BaseURL>${origin.url("segments/")}</BaseURL><SegmentTemplate media="chunk-${'$'}Number${'$'}.m4s" initialization="init.mp4"/></Representation></AdaptationSet></Period></MPD>""".toByteArray()
        val bridge = FfmpegHttpBridge(context)
        try {
            val manifest = URL(bridge.proxyUrl(origin.url("main.mpd"))).readText()
            assertTrue(manifest.contains(bridge.proxyUrl(origin.url("segments/"))))
            assertTrue(manifest.contains("chunk-${'$'}Number${'$'}.m4s"))
            assertTrue(manifest.contains(bridge.proxyUrl(origin.url(""))))
        } finally { bridge.close(); origin.close() }
    }

    private class Origin(var body: ByteArray, private val type: String) : Closeable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val requests = AtomicInteger()
        fun url(path: String) = "http://127.0.0.1:${server.localPort}/$path"
        init {
            Thread({
                while (!server.isClosed) {
                    try {
                        server.accept().use { socket ->
                            val reader = socket.getInputStream().bufferedReader()
                            reader.readLine() ?: return@use
                            while (!reader.readLine().isNullOrEmpty()) { }
                            requests.incrementAndGet()
                            val output = socket.getOutputStream()
                            output.write(("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
                                "Cache-Control: public, max-age=60\r\nConnection: close\r\n\r\n").toByteArray())
                            output.write(body)
                            output.flush()
                        }
                    } catch (_: Exception) { }
                }
            }, "test-media-origin").apply { isDaemon = true; start() }
        }
        override fun close() { server.close() }
    }
}
