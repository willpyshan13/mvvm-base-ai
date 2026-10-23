package com.will.mvvmbase.feature.auth.data

import com.will.mvvmbase.feature.auth.domain.*
import kotlinx.serialization.Serializable
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {
    @POST("auth/login") suspend fun login(@Body request: LoginRequest): Response<TokenResponse>
    @POST("auth/refresh") fun refresh(@Body request: RefreshRequest): Call<TokenResponse>
}
