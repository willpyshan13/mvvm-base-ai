package com.will.mvvmbase.feature.feed.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import retrofit2.Response
import retrofit2.http.*

interface FeedBackend {
    suspend fun page(offset: Int, limit: Int): RemotePage
    /** 409 must carry the latest server item as its JSON body. */
    suspend fun save(item: RemoteItem): Pair<Boolean, RemoteItem>
}
