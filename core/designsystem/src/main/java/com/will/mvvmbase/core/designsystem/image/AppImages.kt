package com.will.mvvmbase.core.designsystem.image

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit

/** One shared loader and cache for public images. Never attach application credentials here. */
object AppImages {
    private const val DISK_BYTES = 128L * 1024 * 1024

    fun install(
        apmInterceptor: Interceptor? = null,
        callFactoryDecorator: (Call.Factory) -> Call.Factory = { it },
    ) {
        SingletonImageLoader.setSafe { context ->
            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
            apmInterceptor?.let { clientBuilder.addNetworkInterceptor(it) }
            val imageCallFactory = callFactoryDecorator(clientBuilder.build())
            ImageLoader.Builder(context)
                .memoryCache {
                    MemoryCache.Builder().maxSizeBytes(
                        minOf(Runtime.getRuntime().maxMemory() / 5, 64L * 1024 * 1024)
                    ).build()
                }
                .diskCache {
                    DiskCache.Builder().directory(context.cacheDir.resolve("image_cache").toOkioPath())
                        .maxSizeBytes(DISK_BYTES).build()
                }
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { imageCallFactory }))
                }
                .crossfade(true)
                .build()
        }
    }

    suspend fun inspect(context: Context): ImageCacheInfo = withContext(Dispatchers.IO) {
        val loader = SingletonImageLoader.get(context.applicationContext)
        ImageCacheInfo(loader.memoryCache?.size ?: 0, loader.memoryCache?.maxSize ?: 0,
            loader.diskCache?.size ?: 0, loader.diskCache?.maxSize ?: 0)
    }

    /** Active requests can populate the cache again after this call. */
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        val loader = SingletonImageLoader.get(context.applicationContext)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }
}
