package com.will.mvvmbase.core.media

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.will.mvvmbase.core.media.internal.FfmpegEngine
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Owns network connections and content grants until this playback session is released. */
internal class FfmpegSourceSession : FfmpegEngine.SourceResolver, Closeable {
    private val closed = AtomicBoolean(false)
    private var bridge: FfmpegHttpBridge? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var temporaryFile: File? = null

    @Synchronized
    override fun resolve(context: Context, source: String): String {
        if (closed.get()) throw IOException("Playback session closed")
        val uri = Uri.parse(source)
        return when (uri.scheme) {
            "https", "http" -> {
                val next = FfmpegHttpBridge(context)
                bridge = next
                next.proxyUrl(source)
            }
            "content" -> {
                val fd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IOException("Unable to open selected video")
                descriptor = fd
                "/proc/self/fd/${fd.fd}"
            }
            "asset", "android.resource" -> {
                val file = File.createTempFile("ffmpeg-input-", ".media", context.cacheDir)
                temporaryFile = file
                val input = if (uri.scheme == "asset") context.assets.open(uri.path.orEmpty().trimStart('/'))
                    else context.contentResolver.openInputStream(uri) ?: throw IOException("Unable to open video resource")
                input.use { stream -> file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (closed.get()) throw IOException("Playback session closed")
                        val count = stream.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                } }
                file.absolutePath
            }
            "file" -> uri.path ?: throw IOException("Invalid file URI")
            null -> source
            else -> throw IOException("Unsupported video source scheme")
        }
    }

    override fun close() {
        closed.set(true)
        synchronized(this) {
            bridge?.close()
            bridge = null
            runCatching { descriptor?.close() }
            descriptor = null
            temporaryFile?.delete()
            temporaryFile = null
        }
    }
}
