package com.will.mvvmbase.feature.home.domain

import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    val taskCount: Flow<Int>

    /** Apply search and status filtering before offset/limit pagination. */
    suspend fun getPage(
        offset: Int,
        limit: Int,
        query: String = "",
        filter: TaskFilter = TaskFilter.ALL,
    ): List<Task>

    suspend fun add(title: String)
    suspend fun remove(id: Long)
    suspend fun update(id: Long, title: String)
    suspend fun setCompleted(id: Long, completed: Boolean)
}
