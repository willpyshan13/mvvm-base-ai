package com.will.mvvmbase.feature.home

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.will.mvvmbase.feature.home.domain.Task
import com.will.mvvmbase.feature.home.domain.TaskFilter
import com.will.mvvmbase.feature.home.domain.TaskRepository
import kotlinx.coroutines.CancellationException

/** Adapts the domain's offset/limit page contract to Paging 3. */
internal class TaskPagingSource(
    private val repository: TaskRepository,
    private val query: String = "",
    private val filter: TaskFilter = TaskFilter.ALL,
) : PagingSource<Int, Task>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Task> {
        val offset = if (params is LoadParams.Prepend) {
            ((params.key ?: 0) - params.loadSize).coerceAtLeast(0)
        } else {
            params.key ?: 0
        }
        val limit = if (params is LoadParams.Prepend) {
            minOf(params.loadSize, params.key ?: 0)
        } else {
            params.loadSize
        }
        return try {
            val tasks = repository.getPage(
                offset = offset,
                limit = limit,
                query = query,
                filter = filter,
            )
            LoadResult.Page(
                data = tasks,
                prevKey = if (offset == 0) null else offset,
                nextKey = if (tasks.size < limit) null else offset + tasks.size,
                itemsBefore = offset,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            LoadResult.Error(exception)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Task>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val loadedOffset = if (state.config.enablePlaceholders) 0 else {
            state.pages.firstOrNull()?.itemsBefore?.takeIf { it >= 0 } ?: return null
        }
        return (loadedOffset + anchorPosition - state.config.initialLoadSize / 2).coerceAtLeast(0)
    }
}
