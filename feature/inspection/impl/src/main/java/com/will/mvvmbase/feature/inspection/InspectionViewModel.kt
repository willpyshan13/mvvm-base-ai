package com.will.mvvmbase.feature.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.will.mvvmbase.feature.inspection.domain.InspectionDraft
import com.will.mvvmbase.feature.inspection.domain.InspectionRecord
import com.will.mvvmbase.feature.inspection.domain.InspectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface InspectionLoadState {
    data object Loading : InspectionLoadState
    data object Missing : InspectionLoadState
    data class Ready(val record: InspectionRecord) : InspectionLoadState
}

sealed interface InspectionListState {
    data object Loading : InspectionListState
    data object Failed : InspectionListState
    data class Ready(val records: List<InspectionRecord>) : InspectionListState
}

@HiltViewModel
class InspectionViewModel @Inject constructor(private val repository: InspectionRepository) : ViewModel() {
    val records: StateFlow<InspectionListState> = repository.observeAll()
        .map<List<InspectionRecord>, InspectionListState> { InspectionListState.Ready(it) }
        .catch { emit(InspectionListState.Failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InspectionListState.Loading)

    private val mutableBusy = MutableStateFlow(false)
    private val mutableError = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    val error = mutableError.asStateFlow()

    fun observe(id: Long): Flow<InspectionLoadState> = repository.observe(id)
        .map { if (it == null) InspectionLoadState.Missing else InspectionLoadState.Ready(it) }
        .catch { emit(InspectionLoadState.Missing) }

    fun showError() { mutableError.value = true }

    fun save(id: Long?, draft: InspectionDraft, complete: Boolean, onSuccess: () -> Unit) {
        action {
            val validated = draft.validated()
            if (complete) require(validated.checkedMask == InspectionRecord.ALL_CHECKS)
            val updated = when {
                id == null -> { repository.create(validated, complete); true }
                complete -> repository.complete(id, validated)
                else -> repository.updateDraft(id, validated)
            }
            if (!updated) throw IllegalStateException("record is no longer editable")
            onSuccess()
        }
    }

    fun delete(id: Long, onSuccess: () -> Unit) = action {
        repository.delete(id)
        onSuccess()
    }

    private fun action(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableError.value = false
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableError.value = true }
            finally { mutableBusy.value = false }
        }
    }
}
