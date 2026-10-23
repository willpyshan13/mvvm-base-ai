package com.will.mvvmbase.feature.auth.domain

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data object SignedIn : AuthState
}
