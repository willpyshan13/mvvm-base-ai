package com.will.mvvmbase.feature.home

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.will.mvvmbase.feature.home.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TaskPagingSourceTest {
    private open class Repository(private val count: Int) : TaskRepository {
        override val taskCount = flowOf(count)
        var criteria: Pair<String, TaskFilter>? = null
        override suspend fun getPage(offset: Int, limit: Int, query: String, filter: TaskFilter): List<Task> {
            criteria = query to filter
            return (offset until minOf(offset + limit, count)).map { Task(it.toLong(), "task") }
        }
        override suspend fun add(title: String) {}
        override suspend fun remove(id: Long) {}
        override suspend fun update(id: Long, title: String) {}
        override suspend fun setCompleted(id: Long, completed: Boolean) {}
    }

    @Test fun `final short page terminates pagination and cancellation propagates`() = runBlocking {
        val repo = Repository(3)
        val source = TaskPagingSource(repo)
        val page = source.load(PagingSource.LoadParams.Refresh(null, 20, false)) as PagingSource.LoadResult.Page
        assertEquals(3, page.data.size)
        assertNull(page.nextKey)
        assertNull(page.prevKey)
        val cancelRepo = object : TaskRepository by repo {
            override suspend fun getPage(offset: Int, limit: Int, query: String, filter: TaskFilter): List<Task> =
                throw CancellationException()
        }
        try {
            TaskPagingSource(cancelRepo).load(PagingSource.LoadParams.Refresh(null, 20, false))
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun `query and status are passed into paginated repository query`() = runBlocking {
        val repo = Repository(3)
        TaskPagingSource(repo, "meeting", TaskFilter.COMPLETED)
            .load(PagingSource.LoadParams.Refresh(null, 20, false))
        assertEquals("meeting" to TaskFilter.COMPLETED, repo.criteria)
    }

    @Test fun `prepend uses exact preceding range when initial and page sizes differ`() = runBlocking {
        val source = TaskPagingSource(Repository(100))
        val first = source.load(PagingSource.LoadParams.Refresh(30, 40, false)) as PagingSource.LoadResult.Page
        val previous = source.load(PagingSource.LoadParams.Prepend(first.prevKey!!, 20, false)) as PagingSource.LoadResult.Page
        assertEquals((10L until 30L).toList(), previous.data.map { it.id })
        val beginning = source.load(PagingSource.LoadParams.Prepend(previous.prevKey!!, 20, false)) as PagingSource.LoadResult.Page
        assertEquals((0L until 10L).toList(), beginning.data.map { it.id })
        assertNull(beginning.prevKey)
    }

    @Test fun `refresh keeps absolute position after earlier pages are dropped`() = runBlocking {
        val source = TaskPagingSource(Repository(200))
        val page = source.load(PagingSource.LoadParams.Refresh(80, 40, false)) as PagingSource.LoadResult.Page
        val state = PagingState(listOf(page), 25, PagingConfig(20, initialLoadSize = 40, enablePlaceholders = false), 0)
        assertEquals(85, source.getRefreshKey(state))
    }
}
