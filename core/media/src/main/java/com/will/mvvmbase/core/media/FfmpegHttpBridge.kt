package com.will.mvvmbase.core.media

import android.content.Context
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Loopback-only gateway: Android handles TLS, FFmpeg reads HTTP and native demuxes/decodes. */
internal class FfmpegHttpBridge(context: Context) : Closeable {
    private val token = UUID.randomUUID().toString()
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val factory = try { AppVideoCache.acquire(context) } catch (failure: Exception) {
        server.close()
        throw failure
    }
    private val prefix = "http://127.0.0.1:${server.localPort}/$token/"
    private val closed = AtomicBoolean(false)
    private val calls: MutableSet<Call> = java.util.Collections.newSetFromMap(ConcurrentHashMap<Call, Boolean>())
    private val sockets: MutableSet<Socket> = java.util.Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())
    private val workers = Executors.newFixedThreadPool(6) { Thread(it, "FFmpeg-HTTPS").apply { isDaemon = true } }

    init {
        Thread({
            while (!closed.get()) {
                try {
                    val socket = server.accept()
                    sockets.add(socket)
                    workers.execute { socket.use { serve(it) }; sockets.remove(socket) }
                } catch (_: IOException) {
                    if (!closed.get()) close()
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    break
                }
            }
        }, "FFmpeg-loopback").apply { isDaemon = true; start() }
    }

    internal fun proxyUrl(url: String): String {
        val uri = URI(url)
        require(uri.scheme in setOf("http", "https") && uri.rawAuthority != null && uri.rawUserInfo == null)
        return prefix + uri.scheme + "/" + uri.rawAuthority + uri.rawPath.ifEmpty { "/" } +
            (uri.rawQuery?.let { "?$it" } ?: "")
    }

    private fun serve(socket: Socket) {
        try {
            socket.soTimeout = 20_000
            val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val requestLine = input.readLine()?.takeIf { it.length <= 8192 } ?: return
            val request = requestLine.split(' ')
            if (request.size < 2 || request[0] !in setOf("GET", "HEAD")) return
            val route = request[1]
            if (!route.startsWith("/$token/")) return
            var range: String? = null
            var headerBytes = 0
            while (true) {
                val line = input.readLine() ?: return
                headerBytes += line.length
                if (headerBytes > 32_768) return
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(':').trim()
            }
            val rest = route.removePrefix("/$token/")
            val url = rest.substringBefore('/') + "://" + rest.substringAfter('/')
            val uri = URI(url)
            if (uri.scheme !in setOf("http", "https") || uri.host == null || uri.rawUserInfo != null) return
            val manifestHint = uri.path.endsWith(".m3u8", true) || uri.path.endsWith(".mpd", true)
            // A zero-offset range can be served as a full response and enter OkHttp's disk cache.
            val actualRange = range?.takeUnless { it == "bytes=0-" }
            val builder = Request.Builder().url(url).header("Accept-Encoding", "identity")
            if (request[0] == "HEAD") builder.head()
            if (actualRange != null) builder.header("Range", actualRange)
            if (manifestHint) builder.cacheControl(CacheControl.FORCE_NETWORK)
            val upstream = builder.build()
            if (actualRange != null && !manifestHint && serveCachedRange(socket, upstream, actualRange)) return
            execute(upstream) { response ->
                val body = response.body
                val type = response.header("Content-Type").orEmpty()
                val isHls = manifestHint && uri.path.endsWith(".m3u8", true) || type.contains("mpegurl", true)
                val isDash = manifestHint && uri.path.endsWith(".mpd", true) || type.contains("dash+xml", true)
                val output = socket.getOutputStream().buffered()
                if (request[0] != "HEAD" && response.isSuccessful && body != null && (isHls || isDash)) {
                    val bytes = body.byteStream().use { it.readBytesAtMost(2 * 1024 * 1024) }
                    val base = URI(response.request.url.toString())
                    val text = bytes.toString(Charsets.UTF_8)
                    val rewritten = if (isHls) rewriteHls(text, base) else rewriteDash(text, base)
                    val content = rewritten.toByteArray(Charsets.UTF_8)
                    headers(output, 200, type, content.size.toLong())
                    if (request[0] != "HEAD") output.write(content)
                } else {
                    headers(output, response.code, type, body?.contentLength() ?: 0,
                        response.header("Content-Range"))
                    if (request[0] != "HEAD") body?.byteStream()?.use { it.copyTo(output) }
                }
                output.flush()
            }
        } catch (_: Exception) {
            // A closed native input is a normal cancellation. Never log signed media URLs.
            runCatching {
                socket.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            }
        }
    }

    private fun serveCachedRange(socket: Socket, request: Request, range: String): Boolean {
        val match = Regex("bytes=(\\d+)-(\\d*)").matchEntire(range) ?: return false
        val start = match.groupValues[1].toLongOrNull() ?: return false
        val end = match.groupValues[2].toLongOrNull()
        var served = false
        val cachedRequest = request.newBuilder().removeHeader("Range").get()
            .cacheControl(CacheControl.FORCE_CACHE).build()
        execute(cachedRequest) { cached ->
            val body = cached.body ?: return@execute
            val size = body.contentLength()
            val type = cached.header("Content-Type").orEmpty()
            if (cached.code != 200 || cached.cacheResponse == null || size <= start || start < 0 ||
                type.contains("mpegurl", true) || type.contains("dash+xml", true)) return@execute
            val last = (end ?: (size - 1)).coerceAtMost(size - 1)
            if (last < start) return@execute
            val length = last - start + 1
            val output = socket.getOutputStream().buffered()
            headers(output, 206, type, length, "bytes $start-$last/$size")
            if (request.method != "HEAD") body.byteStream().use { stream ->
                var remaining = start
                val buffer = ByteArray(64 * 1024)
                while (remaining > 0) {
                    val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) throw IOException("Incomplete cached video")
                    remaining -= read
                }
                remaining = length
                while (remaining > 0) {
                    val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) throw IOException("Incomplete cached video")
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
            output.flush()
            served = true
        }
        return served
    }

    private fun execute(request: Request, block: (Response) -> Unit) {
        if (closed.get()) throw IOException("Playback session closed")
        val call = factory.newCall(request)
        calls.add(call)
        if (closed.get()) call.cancel()
        try { call.execute().use(block) } finally { calls.remove(call) }
    }

    private fun headers(output: java.io.OutputStream, code: Int, type: String, length: Long, contentRange: String? = null) {
        val value = buildString {
            append("HTTP/1.1 $code Response\r\nConnection: close\r\nAccept-Ranges: bytes\r\n")
            if (length >= 0) append("Content-Length: $length\r\n")
            if (type.isNotEmpty()) append("Content-Type: $type\r\n")
            contentRange?.let { append("Content-Range: $it\r\n") }
            append("\r\n")
        }
        output.write(value.toByteArray(Charsets.ISO_8859_1))
    }

    private fun rewriteHls(text: String, base: URI): String = text.lineSequence().joinToString("\n") { line ->
        if (line.startsWith('#')) Regex("URI=\"([^\"]+)\"").replace(line) {
            "URI=\"${proxyUrl(base.resolve(it.groupValues[1]).toString())}\""
        } else if (line.isNotBlank()) proxyUrl(base.resolve(line.trim()).toString()) else line
    }

    private fun rewriteDash(text: String, base: URI): String {
        if (Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            throw IOException("External XML declarations are not supported in media manifests")
        }
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(text.byteInputStream())
        fun rewrite(element: org.w3c.dom.Element, inherited: URI) {
            val children = (0 until element.childNodes.length).map { element.childNodes.item(it) }
                .filterIsInstance<org.w3c.dom.Element>()
            val bases = children.filter { it.localName == "BaseURL" }
            val effective = bases.firstOrNull()?.textContent?.trim()?.let(inherited::resolve) ?: inherited
            bases.forEach { it.textContent = proxyUrl(inherited.resolve(it.textContent.trim()).toString()) }
            for (name in listOf("media", "initialization", "sourceURL", "index", "href")) {
                val value = element.getAttribute(name)
                if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith('/')) {
                    element.setAttribute(name, proxyUrl(effective.resolve(value).toString()))
                }
            }
            children.filter { it.localName != "BaseURL" }.forEach { rewrite(it, effective) }
        }
        val root = document.documentElement
        val hasBase = (0 until root.childNodes.length).any { root.childNodes.item(it).localName == "BaseURL" }
        rewrite(root, base.resolve("."))
        if (!hasBase) {
            val node = document.createElementNS(root.namespaceURI, "BaseURL")
            node.textContent = proxyUrl(base.resolve(".").toString())
            root.insertBefore(node, root.firstChild)
        }
        val output = java.io.StringWriter()
        javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(
            javax.xml.transform.dom.DOMSource(document), javax.xml.transform.stream.StreamResult(output))
        return output.toString()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { server.close() }
        calls.forEach { it.cancel() }
        sockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
        // Workers may still be closing response bodies; don't close the shared cache under them.
        Thread({
            workers.awaitTermination(25, java.util.concurrent.TimeUnit.SECONDS)
            runCatching { AppVideoCache.release() }
        }, "FFmpeg-HTTPS-close").apply { isDaemon = true; start() }
    }
}

private fun java.io.InputStream.readBytesAtMost(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (output.size() + count > limit) throw IOException("Media manifest too large")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
