package com.will.mvvmbase.feature.auth.domain

sealed interface LoginResult {
    data object Success : LoginResult
    data class Failure(val message: String) : LoginResult
}
