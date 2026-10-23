package com.will.mvvmbase.feature.auth.domain

data class AuthSessionTokens(
    val accessToken: String,
    val refreshToken: String? = null,
)
