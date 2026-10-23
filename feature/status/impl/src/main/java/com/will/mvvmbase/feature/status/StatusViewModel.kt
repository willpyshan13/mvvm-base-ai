package com.will.mvvmbase.feature.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.will.mvvmbase.feature.status.domain.StatusRepository
import com.will.mvvmbase.feature.status.domain.StatusResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class StatusUiState(
    val isLoading: Boolean = true,
    val result: StatusResult? = null,
)

@HiltViewModel
class StatusViewModel @Inject constructor(
    private val repository: StatusRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = mutableState

    init { viewModelScope.launch { load() } }

    fun check() {
        if (mutableState.value.isLoading) return
        viewModelScope.launch {
            load()
        }
    }

    private suspend fun load() {
        mutableState.value = StatusUiState(isLoading = true)
        mutableState.value = StatusUiState(isLoading = false, result = repository.check())
    }
}
