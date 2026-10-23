package com.will.mvvmbase.core.platform

import android.content.Context
import android.net.Uri
import androidx.work.*
import java.io.File
import java.io.IOException
import java.io.FileOutputStream
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink

/** Persistent, bounded, cancellable file transfers. Files remain private to this app. */
object AppTransfers {
    @Volatile private var clientHooks = ClientHooks()

    /** Lets the application add cross-cutting OkHttp instrumentation without a platform dependency on it. */
    fun configureHttpClientHooks(
        networkInterceptor: Interceptor?,
        callFactoryDecorator: (Call.Factory) -> Call.Factory,
    ) {
        clientHooks = ClientHooks(networkInterceptor, callFactoryDecorator)
    }

    fun enqueue(context: Context, url: String, upload: Uri? = null, resumeId: UUID? = null): UUID {
        require(url.toHttpUrl().isHttps) { "HTTPS required" }
        val request = OneTimeWorkRequestBuilder<TransferWorker>()
            .setInputData(workDataOf("url" to url, "upload" to upload?.toString(),
                "transferId" to resumeId?.toString()))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag("file-transfer").build()
        WorkManager.getInstance(context).enqueue(request)
        return request.id
    }

    internal fun createHttpClient(): Call.Factory {
        val hooks = clientHooks
        val builder = OkHttpClient.Builder()
            .callTimeout(8, TimeUnit.MINUTES)
            .followRedirects(false)
        hooks.networkInterceptor?.let { builder.addNetworkInterceptor(it) }
        return hooks.callFactoryDecorator(builder.build())
    }

    fun cancel(context: Context, id: UUID) { WorkManager.getInstance(context).cancelWorkById(id) }

    private data class ClientHooks(
        val networkInterceptor: Interceptor? = null,
        val callFactoryDecorator: (Call.Factory) -> Call.Factory = { it },
    )
}

class TransferWorker @JvmOverloads constructor(context: Context, parameters: WorkerParameters,
    private val client: Call.Factory = AppTransfers.createHttpClient(),
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString("url") ?: return@withContext Result.failure()
        val directory = File(applicationContext.filesDir, "downloads").apply { mkdirs() }
        val transferId = inputData.getString("transferId") ?: id.toString()
        val partial = File(directory, "$transferId.part")
        val validatorFile = File(directory, "$transferId.validator")
        val urlFile = File(directory, "$transferId.url")
        var retainPartial = false
        val upload = inputData.getString("upload")?.let(Uri::parse)
        var call: Call? = null
        // Cancel the HTTP socket as soon as WorkManager cancels this coroutine.
        val cancellationWatcher = CoroutineScope(kotlin.coroutines.coroutineContext).launch(Dispatchers.Default) {
            try { awaitCancellation() } finally { call?.cancel() }
        }
        try {
            require(url.toHttpUrl().isHttps)
            val request = Request.Builder().url(url)
            var offset = 0L
            if (upload == null) {
                request.header("Accept-Encoding", "identity")
                if (!urlFile.exists() || urlFile.readText() != url) {
                    partial.delete()
                    validatorFile.delete()
                    urlFile.writeText(url)
                }
                val validator = validatorFile.takeIf { it.exists() }?.readText()
                if (partial.exists() && !validator.isNullOrBlank()) {
                    offset = partial.length()
                    if (offset > 0) request.header("Range", "bytes=$offset-").header("If-Range", validator)
                }
            }
            if (upload != null) {
                request.header("Idempotency-Key", id.toString())
                request.post(object : RequestBody() {
                    override fun contentType() = "application/octet-stream".toMediaType()
                    override fun writeTo(sink: BufferedSink) {
                        applicationContext.contentResolver.openInputStream(upload).use { stream ->
                            requireNotNull(stream) { "File no longer accessible" }
                            val buffer = ByteArray(32 * 1024)
                            var total = 0L
                            var lastReported = 0L
                            while (true) {
                                if (isStopped) throw IOException("Cancelled")
                                val count = stream.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= MAX_BYTES) { "Transfer exceeds 100 MiB" }
                                sink.write(buffer, 0, count)
                                if (total - lastReported >= 256 * 1024) {
                                    setProgressAsync(workDataOf("bytes" to total))
                                    lastReported = total
                                }
                            }
                        }
                    }
                })
            }
            call = client.newCall(request.build())
            call!!.execute().use { response ->
                if (response.code == 429 || response.code >= 500 || response.code == 408) {
                    retainPartial = upload == null
                    return@withContext retryOrFail()
                }
                if (upload == null && response.code == 416) {
                    // The remote representation may have changed. Retry from zero.
                    partial.delete()
                    validatorFile.delete()
                    return@withContext retryOrFail()
                }
                if (!response.isSuccessful) return@withContext Result.failure(workDataOf("error" to "HTTP ${response.code}"))
                if (upload == null) {
                    val body = requireNotNull(response.body)
                    var expected = body.contentLength()
                    if (response.code == 206) {
                        val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
                            .matchEntire(response.header("Content-Range").orEmpty())
                            ?: throw IOException("Invalid Content-Range")
                        val (start, end, size) = range.destructured
                        if (start.toLong() != offset || end.toLong() < offset || end.toLong() >= size.toLong()) {
                            throw IOException("Unexpected download range")
                        }
                        expected = size.toLong()
                    } else {
                        // A server ignoring Range, or a changed validator, returns the full file.
                        offset = 0L
                    }
                    require(expected <= MAX_BYTES) { "Transfer exceeds 100 MiB" }
                    val validator = response.header("ETag")?.takeUnless { it.startsWith("W/") }
                        ?: response.header("Last-Modified")
                    if (response.code == 206 && validator != null && validatorFile.exists() &&
                        validator != validatorFile.readText()) {
                        partial.delete()
                        validatorFile.delete()
                        throw IOException("Remote file changed")
                    }
                    if (offset == 0L) {
                        // Truncate before updating metadata so interrupted writes never mix versions.
                        FileOutputStream(partial, false).close()
                        if (validator == null) validatorFile.delete() else validatorFile.writeText(validator)
                    }
                    var total = offset
                    var sampleBytes = total
                    var sampleTime = SystemClock.elapsedRealtime()
                    setProgress(workDataOf("bytes" to total, "total" to expected, "weak" to false))
                    body.byteStream().use { source -> FileOutputStream(partial, true).use { target ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            if (isStopped) throw IOException("Cancelled")
                            val count = source.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_BYTES) { "Transfer exceeds 100 MiB" }
                            target.write(buffer, 0, count)
                            val now = SystemClock.elapsedRealtime()
                            if (now - sampleTime >= 1_000) {
                                val speed = (total - sampleBytes) * 1_000 / (now - sampleTime)
                                setProgress(workDataOf("bytes" to total, "total" to expected,
                                    "bytesPerSecond" to speed, "weak" to (speed < 50 * 1024)))
                                sampleBytes = total
                                sampleTime = now
                            }
                        }
                        if (expected >= 0 && expected != total) throw IOException("Incomplete response")
                    } }
                    setProgress(workDataOf("bytes" to total, "total" to total, "weak" to false))
                    val output = File(directory, "$transferId.bin")
                    check(partial.renameTo(output))
                    Result.success(workDataOf("file" to output.absolutePath))
                } else Result.success()
            }
        } catch (cancelled: CancellationException) { retainPartial = upload == null; throw cancelled }
        catch (_: IOException) { retainPartial = upload == null; retryOrFail() }
        catch (_: Exception) { Result.failure(workDataOf("error" to "file_unavailable_or_too_large")) }
        finally {
            if (!retainPartial) { partial.delete(); validatorFile.delete(); urlFile.delete() }
            cancellationWatcher.cancel()
            call?.cancel()
        }
    }
    private fun retryOrFail() = if (runAttemptCount < 3) Result.retry() else Result.failure(workDataOf("error" to "retries_exhausted"))
    companion object { private const val MAX_BYTES = 100L * 1024 * 1024 }
}
