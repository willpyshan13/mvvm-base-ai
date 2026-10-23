package com.will.mvvmbase.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.will.mvvmbase.feature.feed.domain.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class FeedViewModel @Inject constructor(private val repository: FeedRepository) : ViewModel() {
    val items = repository.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val mutableBusy = MutableStateFlow(false)
    private val mutableError = MutableStateFlow(false)
    private val mutableHasMore = MutableStateFlow(true)
    val busy = mutableBusy.asStateFlow()
    val error = mutableError.asStateFlow()
    val hasMore = mutableHasMore.asStateFlow()
    val isDemo = repository.isDemo
    fun refresh() = action { repository.refresh(); mutableHasMore.value = true }
    fun more() = action { mutableHasMore.value = repository.loadMore() }
    fun add(title: String, onSaved: () -> Unit) = action { repository.add(title); onSaved() }
    fun edit(id: String, title: String, onSaved: () -> Unit = {}) = action {
        repository.rename(id, title)
        onSaved()
    }
    fun sync() = action { repository.sync() }
    fun resolve(id: String, keepLocal: Boolean) = action { repository.resolve(id, keepLocal) }
    fun conflict(id: String) = action { repository.simulateConflict(id); repository.sync() }
    fun offline(value: Boolean) = repository.simulateOffline(value)
    private fun action(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            try { block(); mutableError.value = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableError.value = true }
            finally { mutableBusy.value = false }
        }
    }
}
