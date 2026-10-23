package com.will.mvvmbase.feature.home

import com.will.mvvmbase.feature.home.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private class Repository : TaskRepository {
        override val taskCount = flowOf(0)
        var calls = 0
        var fail = false
        val finish = CompletableDeferred<Unit>()
        override suspend fun getPage(offset: Int, limit: Int, query: String, filter: TaskFilter) = emptyList<Task>()
        override suspend fun update(id: Long, title: String) { calls++; finish.await(); if (fail) error("disk full") }
        override suspend fun setCompleted(id: Long, completed: Boolean) { calls++ }
        override suspend fun add(title: String) { calls++; finish.await(); if (fail) error("disk full") }
        override suspend fun remove(id: Long) { }
    }
    @Test fun `duplicate submission is ignored and draft clears only after successful save`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = Repository(); val vm = HomeViewModel(repo)
            var cleared = 0
            vm.addTask("one") { cleared++ }; vm.addTask("one") { cleared++ }
            runCurrent()
            assertEquals(1, repo.calls); assertEquals(0, cleared)
            repo.finish.complete(Unit); advanceUntilIdle()
            assertEquals(1, cleared)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `failed save retains draft and permits retry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = Repository().apply { fail = true; finish.complete(Unit) }
            val vm = HomeViewModel(repo)
            var cleared = false
            vm.addTask("keep") { cleared = true }; advanceUntilIdle()
            assertFalse(cleared)
            repo.fail = false
            vm.addTask("keep") { cleared = true }; advanceUntilIdle()
            assertTrue(cleared); assertEquals(2, repo.calls)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `failed edit keeps dialog open and successful retry closes it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = Repository().apply { fail = true; finish.complete(Unit) }
            val vm = HomeViewModel(repo)
            var closed = false
            vm.updateTask(1, "edited") { closed = true }
            advanceUntilIdle()
            assertFalse(closed)
            repo.fail = false
            vm.updateTask(1, "edited") { closed = true }
            advanceUntilIdle()
            assertTrue(closed)
            assertEquals(2, repo.calls)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `completion cannot race with an in-flight edit`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = Repository()
            val vm = HomeViewModel(repo)
            vm.updateTask(1, "edited")
            runCurrent()
            vm.setTaskCompleted(1, true)
            assertEquals(1, repo.calls)
            repo.finish.complete(Unit)
            advanceUntilIdle()
            vm.setTaskCompleted(1, true)
            advanceUntilIdle()
            assertEquals(2, repo.calls)
        } finally { Dispatchers.resetMain() }
    }
}
