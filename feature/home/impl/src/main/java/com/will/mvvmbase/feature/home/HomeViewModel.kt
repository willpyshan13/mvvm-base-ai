package com.will.mvvmbase.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.will.mvvmbase.feature.home.domain.TaskRepository
import com.will.mvvmbase.feature.home.domain.TaskFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isSubmitting: Boolean = false,
    val taskCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessageRes: Int? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: TaskRepository) : ViewModel() {
    private val submitting = MutableStateFlow(false)
    private val operationError = MutableStateFlow<Int?>(null)
    private val mutableSearchQuery = MutableStateFlow("")
    private val mutableTaskFilter = MutableStateFlow(TaskFilter.ALL)
    val searchQuery: StateFlow<String> = mutableSearchQuery.asStateFlow()
    val taskFilter: StateFlow<TaskFilter> = mutableTaskFilter.asStateFlow()
    @Volatile
    private var currentTaskPagingSource: TaskPagingSource? = null

    private val taskCriteria = combine(mutableSearchQuery, mutableTaskFilter) { query, filter ->
        query.trim() to filter
    }
        .distinctUntilChanged()
    val pagedTasks = taskCriteria.flatMapLatest { (query, filter) ->
            Pager(
                config = PagingConfig(
                    pageSize = 20,
                    initialLoadSize = 40,
                    prefetchDistance = 5,
                    enablePlaceholders = false,
                ),
                pagingSourceFactory = {
                    TaskPagingSource(repository, query, filter).also {
                        currentTaskPagingSource = it
                    }
                },
            ).flow
        }
        .cachedIn(viewModelScope)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.taskCount
            .map { count -> HomeUiState(taskCount = count, isLoading = false) }
            .onStart { emit(HomeUiState()) }
            .catch { emit(HomeUiState(isLoading = false, errorMessageRes = R.string.task_read_failed)) },
        operationError,
        submitting,
    ) { state, error, submitting ->
        state.copy(
            errorMessageRes = error ?: state.errorMessageRes,
            isSubmitting = submitting,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setSearchQuery(query: String) {
        mutableSearchQuery.value = query
    }

    fun setTaskFilter(filter: TaskFilter) {
        mutableTaskFilter.value = filter
    }

    fun addTask(title: String, onSuccess: () -> Unit = {}) {
        if (submitting.value || title.isBlank()) return
        submitting.value = true
        viewModelScope.launch {
            try {
                repository.add(title)
                onSuccess()
                currentTaskPagingSource?.invalidate()
                operationError.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                operationError.value = R.string.task_add_failed
            } finally { submitting.value = false }
        }
    }

    fun removeTask(id: Long) {
        if (submitting.value) return
        submitting.value = true
        viewModelScope.launch {
            try {
                repository.remove(id)
                currentTaskPagingSource?.invalidate()
                operationError.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                operationError.value = R.string.task_delete_failed
            } finally { submitting.value = false }
        }
    }

    fun updateTask(id: Long, title: String, onSuccess: () -> Unit = {}) {
        if (submitting.value || title.isBlank()) return
        submitting.value = true
        viewModelScope.launch {
            try {
                repository.update(id, title)
                onSuccess()
                currentTaskPagingSource?.invalidate()
                operationError.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                operationError.value = R.string.task_save_failed
            } finally {
                submitting.value = false
            }
        }
    }

    fun setTaskCompleted(id: Long, completed: Boolean) {
        if (submitting.value) return
        submitting.value = true
        viewModelScope.launch {
            try {
                repository.setCompleted(id, completed)
                currentTaskPagingSource?.invalidate()
                operationError.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                operationError.value = R.string.task_status_failed
            } finally {
                submitting.value = false
            }
        }
    }
}

