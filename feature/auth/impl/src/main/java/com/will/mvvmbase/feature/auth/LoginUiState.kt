package com.will.mvvmbase.feature.auth

internal data class LoginUiState(
    val isSubmitting: Boolean = false,
    val errorMessageRes: Int? = null,
)

internal sealed interface LoginEvent {
    data object Succeeded : LoginEvent
}
