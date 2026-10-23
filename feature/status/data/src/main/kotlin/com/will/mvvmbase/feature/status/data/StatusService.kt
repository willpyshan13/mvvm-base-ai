package com.will.mvvmbase.feature.status.data

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET

@Serializable
data class StatusDto(val status: String)

interface StatusService {
    @GET("status")
    suspend fun status(): Response<StatusDto>
}
