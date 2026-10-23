package com.will.mvvmbase.feature.feed.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import retrofit2.Response
import retrofit2.http.*

@Serializable data class RemoteItem(val id: String, val title: String, val version: Long)
