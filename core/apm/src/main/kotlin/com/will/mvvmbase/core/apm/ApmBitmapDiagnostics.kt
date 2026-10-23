package com.will.mvvmbase.core.apm

import android.graphics.Bitmap
import android.os.Build
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.util.LinkedHashMap

/** Opt-in bitmap content checker. It hashes at most 1 MiB of pixels per call and retains no bitmap. */
internal object ApmBitmapDiagnostics {
    private data class Entry(val bitmap: WeakReference<Bitmap>, val owner: String)
    private val entries = object : LinkedHashMap<String, Entry>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > 128
    }

    @Synchronized
    fun inspect(bitmap: Bitmap, owner: String) {
        if (!AppApm.isInitialized || bitmap.isRecycled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) return
        val pixelsCount = bitmap.width.toLong() * bitmap.height
        if (pixelsCount <= 0 || pixelsCount > 262_144) return
        runCatching {
            val pixels = IntArray(pixelsCount.toInt())
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val digest = MessageDigest.getInstance("SHA-256")
            pixels.forEach { pixel ->
                digest.update((pixel ushr 24).toByte())
                digest.update((pixel ushr 16).toByte())
                digest.update((pixel ushr 8).toByte())
                digest.update(pixel.toByte())
            }
            val key = "${bitmap.width}x${bitmap.height}:" + digest.digest().joinToString("") { "%02x".format(it) }
            val previous = entries[key]
            val previousBitmap = previous?.bitmap?.get()
            if (previousBitmap != null && previousBitmap !== bitmap && !previousBitmap.isRecycled) {
                AppApm.recordEvent("app.bitmap.duplicate", mapOf("owner" to owner.take(80), "previous_owner" to previous.owner.take(80)))
            } else {
                entries[key] = Entry(WeakReference(bitmap), owner.take(80))
            }
        }
    }
}
