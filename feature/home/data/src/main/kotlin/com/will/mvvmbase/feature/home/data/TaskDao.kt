package com.will.mvvmbase.feature.home.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT COUNT(*) FROM tasks")
    fun observeTaskCount(): Flow<Int>

    @Query("SELECT * FROM tasks ORDER BY id DESC LIMIT :limit OFFSET :offset")
    suspend fun getTasksPage(limit: Int, offset: Int): List<TaskEntity>

    @Query(
        """SELECT * FROM tasks
            WHERE (:query = '' OR instr(lower(title), lower(:query)) > 0)
            AND (:filter = -1 OR (:filter = 0 AND isCompleted = 0) OR (:filter = 1 AND isCompleted = 1))
            ORDER BY id DESC LIMIT :limit OFFSET :offset""",
    )
    suspend fun searchTasksPage(
        query: String,
        filter: Int,
        limit: Int,
        offset: Int,
    ): List<TaskEntity>

    @Insert
    suspend fun insert(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE tasks SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE tasks SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)
}

