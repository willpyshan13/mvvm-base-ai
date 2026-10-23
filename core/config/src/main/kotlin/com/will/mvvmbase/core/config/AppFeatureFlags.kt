package com.will.mvvmbase.core.config

import android.content.Context
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** Public, non-sensitive kill switches. Unknown server keys are ignored. */
enum class FeatureFlag(val wireName: String, val defaultEnabled: Boolean) {
    FEED_SYNC("feed.sync", true),
    VIDEO_PLAYER("media.video_player", true),
}

object AppFeatureFlags {
    private const val MAX_RESPONSE_BYTES = 65_536
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient()
    private val state = MutableStateFlow(FeatureFlag.entries.associateWith { it.defaultEnabled })

    val values: StateFlow<Map<FeatureFlag, Boolean>> = state

    fun enabled(flag: FeatureFlag): Boolean = state.value[flag] ?: flag.defaultEnabled

    @Synchronized
    fun initialize(context: Context, endpoint: String) {
        if (endpoint.isBlank()) return
        require(endpoint.startsWith("https://")) { "Feature flags require HTTPS" }
        val prefs = context.applicationContext.getSharedPreferences("feature-flags", Context.MODE_PRIVATE)
        if (prefs.getString("source_url", null) == endpoint) {
            parse(prefs.getString("last_good", null))?.let { state.value = it }
        } else {
            state.value = FeatureFlag.entries.associateWith { it.defaultEnabled }
        }
        scope.launch {
            while (isActive) {
                runCatching {
                    val request = Request.Builder().url(endpoint).get().build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body ?: return@use
                            val buffer = ByteArrayOutputStream()
                            body.byteStream().use { input ->
                                val chunk = ByteArray(4096)
                                while (buffer.size() <= MAX_RESPONSE_BYTES) {
                                    val count = input.read(chunk)
                                    if (count < 0) break
                                    buffer.write(chunk, 0, count)
                                }
                            }
                            if (buffer.size() <= MAX_RESPONSE_BYTES) {
                                val raw = buffer.toString(Charsets.UTF_8.name())
                                parse(raw)?.let { flags ->
                                    state.value = flags
                                    prefs.edit().putString("source_url", endpoint)
                                        .putString("last_good", raw).apply()
                                }
                            }
                        }
                    }
                }
                delay(15 * 60 * 1000L)
            }
        }
    }

    internal fun parse(raw: String?): Map<FeatureFlag, Boolean>? = runCatching {
        val features = Json.parseToJsonElement(raw ?: return null).jsonObject["features"]?.jsonObject ?: return null
        FeatureFlag.entries.associateWith { flag ->
            features[flag.wireName]?.jsonPrimitive?.booleanOrNull ?: flag.defaultEnabled
        }
    }.getOrNull()
}
