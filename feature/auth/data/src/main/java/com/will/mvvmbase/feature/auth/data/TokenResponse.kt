package com.will.mvvmbase.feature.auth.data

import com.will.mvvmbase.feature.auth.domain.*
import kotlinx.serialization.Serializable
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable data class TokenResponse(val accessToken: String, val refreshToken: String? = null)
