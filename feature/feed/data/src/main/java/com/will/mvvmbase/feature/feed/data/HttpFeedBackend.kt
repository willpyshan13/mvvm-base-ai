package com.will.mvvmbase.feature.feed.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import retrofit2.Response
import retrofit2.http.*

class HttpFeedBackend(private val service: FeedService) : FeedBackend {
    override suspend fun page(offset: Int, limit: Int) = service.page(offset, limit)
    override suspend fun save(item: RemoteItem): Pair<Boolean, RemoteItem> {
        val response = service.save(item.id, item)
        if (response.code() == 409) {
            val latest = response.errorBody()?.use { Json.decodeFromString<RemoteItem>(it.string()) }
                ?: throw IOException("Conflict body missing")
            return (latest.title == item.title && latest.version > item.version) to latest
        }
        if (!response.isSuccessful) { response.errorBody()?.close(); throw IOException("HTTP ${response.code()}") }
        return true to requireNotNull(response.body())
    }
}
