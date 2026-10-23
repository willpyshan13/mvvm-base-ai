package com.will.mvvmbase.core.media

import android.content.Context
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Shared HTTP cache and credential-free HTTPS client for the FFmpeg bridge. */
object AppVideoCacheSettings {
    fun configureHttpClientHooks(
        networkInterceptor: Interceptor? = null,
        callFactoryDecorator: ((Call.Factory) -> Call.Factory)? = null,
    ) = AppVideoCache.configureHooks(networkInterceptor, callFactoryDecorator)

    fun configure(context: Context, maxSizeMb: Int) {
        require(maxSizeMb >= 0)
        AppVideoCache.configure(context.applicationContext, maxSizeMb.toLong() * 1024 * 1024)
    }

    /** Disk inspection and clearing should be called on a worker dispatcher. */
    fun inspect(context: Context): VideoCacheInfo = AppVideoCache.inspect(context.applicationContext)
    fun clear(context: Context): Boolean = AppVideoCache.clear(context.applicationContext)
}

internal object AppVideoCache {
    private const val DIRECTORY = "ffmpeg-http-cache"
    private var maximumBytes = 512L * 1024 * 1024
    private var activePlayers = 0
    private var cache: Cache? = null
    private var directory: File? = null
    private var interceptor: Interceptor? = null
    private var decorator: ((Call.Factory) -> Call.Factory)? = null

    @Synchronized
    fun configureHooks(value: Interceptor?, wrapper: ((Call.Factory) -> Call.Factory)?) {
        interceptor = value
        decorator = wrapper
    }

    @Synchronized
    fun configure(context: Context, bytes: Long) {
        directory = File(context.cacheDir, DIRECTORY)
        if (maximumBytes == bytes) return
        maximumBytes = bytes
        if (activePlayers == 0) closeCache()
    }

    @Synchronized
    fun acquire(context: Context): Call.Factory {
        val root = File(context.cacheDir, DIRECTORY)
        directory = root
        if (cache == null && maximumBytes > 0) cache = Cache(root, maximumBytes)
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followSslRedirects(false)
            .cache(cache)
            .apply { interceptor?.let { addNetworkInterceptor(it) } }
            .build()
        val factory = decorator?.invoke(client) ?: client
        activePlayers++
        return factory
    }

    @Synchronized
    fun release() {
        activePlayers = (activePlayers - 1).coerceAtLeast(0)
        if (activePlayers == 0) {
            if (cache?.maxSize() != maximumBytes) closeCache()
            else cache?.flush()
        }
    }

    @Synchronized
    fun inspect(context: Context): VideoCacheInfo {
        val root = File(context.cacheDir, DIRECTORY)
        val used = cache?.size() ?: root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return VideoCacheInfo(used, maximumBytes, activePlayers)
    }

    @Synchronized
    fun clear(context: Context): Boolean {
        if (activePlayers != 0) return false
        closeCache()
        val root = File(context.cacheDir, DIRECTORY)
        return !root.exists() || root.deleteRecursively()
    }

    private fun closeCache() {
        cache?.close()
        cache = null
        if (maximumBytes == 0L) directory?.deleteRecursively()
    }
}
