package com.will.mvvmbase.feature.feed.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import retrofit2.Response
import retrofit2.http.*

interface FeedService {
    @GET("items") suspend fun page(@Query("offset") offset: Int, @Query("limit") limit: Int): RemotePage
    @PUT("items/{id}") suspend fun save(@Path("id") id: String, @Body item: RemoteItem): Response<RemoteItem>
}
