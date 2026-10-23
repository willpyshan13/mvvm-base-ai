package com.will.mvvmbase.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.will.mvvmbase.feature.auth.domain.AuthRepository
import com.will.mvvmbase.feature.auth.domain.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LoginUiState())
    internal val uiState = mutableUiState.asStateFlow()

    private val eventChannel = Channel<LoginEvent>(Channel.BUFFERED)
    internal val events = eventChannel.receiveAsFlow()
    private val loginInProgress = AtomicBoolean(false)

    fun login(username: String, password: String) {
        if (!loginInProgress.compareAndSet(false, true)) return
        if (username.isBlank() || password.isBlank()) {
            loginInProgress.set(false)
            mutableUiState.value = LoginUiState(errorMessageRes = R.string.login_missing_input)
            return
        }

        viewModelScope.launch {
            mutableUiState.value = LoginUiState(isSubmitting = true)
            try {
                when (val result = authRepository.login(username.trim(), password)) {
                    LoginResult.Success -> {
                        mutableUiState.value = LoginUiState()
                        eventChannel.send(LoginEvent.Succeeded)
                    }
                    is LoginResult.Failure -> {
                        mutableUiState.value = LoginUiState(errorMessageRes = R.string.login_failed)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableUiState.value = LoginUiState(errorMessageRes = R.string.login_failed)
            } finally {
                loginInProgress.set(false)
                if (mutableUiState.value.isSubmitting) {
                    mutableUiState.value = LoginUiState()
                }
            }
        }
    }
}
