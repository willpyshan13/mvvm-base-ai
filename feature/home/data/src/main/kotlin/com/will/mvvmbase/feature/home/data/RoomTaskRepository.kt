package com.will.mvvmbase.feature.home.data

import com.will.mvvmbase.feature.home.domain.Task
import com.will.mvvmbase.feature.home.domain.TaskFilter
import com.will.mvvmbase.feature.home.domain.TaskRepository
import javax.inject.Inject

class RoomTaskRepository @Inject constructor(private val dao: TaskDao) : TaskRepository {
    override val taskCount = dao.observeTaskCount()

    override suspend fun getPage(
        offset: Int,
        limit: Int,
        query: String,
        filter: TaskFilter,
    ): List<Task> {
        require(offset >= 0) { "Offset cannot be negative." }
        require(limit > 0) { "Page size must be positive." }
        return dao.searchTasksPage(
            query = query.trim(),
            filter = filter.databaseValue,
            limit = limit,
            offset = offset,
        ).map { row -> Task(row.id, row.title, row.isCompleted) }
    }

    override suspend fun add(title: String) {
        val normalized = title.trim()
        if (normalized.isNotEmpty()) dao.insert(TaskEntity(title = normalized))
    }

    override suspend fun remove(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun update(id: Long, title: String) {
        val normalized = title.trim()
        if (normalized.isNotEmpty()) dao.updateTitle(id, normalized)
    }

    override suspend fun setCompleted(id: Long, completed: Boolean) {
        dao.setCompleted(id, completed)
    }
}

