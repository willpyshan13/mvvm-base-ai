package com.will.mvvmbase.feature.home.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.will.mvvmbase.feature.home.domain.TaskFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTaskRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "repository-test.db"

    @Before fun clearDatabase() {
        context.deleteDatabase(databaseName)
    }

    @After fun removeDatabase() {
        context.deleteDatabase(databaseName)
    }

    @Test fun tasksPersistAfterDatabaseReopen() = runBlocking {
        val firstDatabase = Room.databaseBuilder(context, TaskDatabase::class.java, databaseName).build()
        try {
            val repository = RoomTaskRepository(firstDatabase.taskDao())
            repository.add("  First task  ")
            repository.add(" ")
            assertEquals(1, repository.taskCount.first())
            assertEquals(listOf("First task"), repository.getPage(offset = 0, limit = 10).map { it.title })
        } finally {
            firstDatabase.close()
        }

        val reopenedDatabase = Room.databaseBuilder(context, TaskDatabase::class.java, databaseName).build()
        try {
            val repository = RoomTaskRepository(reopenedDatabase.taskDao())
            val savedTask = repository.getPage(offset = 0, limit = 10).single()
            assertEquals("First task", savedTask.title)
            repository.remove(savedTask.id)
            assertEquals(0, repository.taskCount.first())
            assertEquals(emptyList<String>(), repository.getPage(offset = 0, limit = 10))
        } finally {
            reopenedDatabase.close()
        }
    }
    @Test fun migrationPreservesVersionOneTasksAndPersistsEdits() = runBlocking {
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL)")
            old.execSQL("INSERT INTO tasks (id, title) VALUES (7, 'Existing task')")
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES (42, '37aa7486b1f39897fbaed089e4d171d6')")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, TaskDatabase::class.java, databaseName)
            .addMigrations(TaskDatabase.MIGRATION_1_2)
            .build()
        try {
            val repository = RoomTaskRepository(migrated.taskDao())
            val task = repository.getPage(0, 10).single()
            assertEquals(7L, task.id)
            assertEquals("Existing task", task.title)
            assertEquals(false, task.isCompleted)
            repository.update(7, "  Edited task  ")
            repository.setCompleted(7, true)
        } finally {
            migrated.close()
        }
        val reopened = Room.databaseBuilder(context, TaskDatabase::class.java, databaseName).build()
        try {
            val task = RoomTaskRepository(reopened.taskDao()).getPage(0, 10).single()
            assertEquals("Edited task", task.title)
            assertEquals(true, task.isCompleted)
        } finally {
            reopened.close()
        }
    }

    @Test fun searchAndStatusFilterBeforePaginationAndTreatWildcardsLiterally() = runBlocking {
        val database = Room.databaseBuilder(context, TaskDatabase::class.java, databaseName).build()
        try {
            val repository = RoomTaskRepository(database.taskDao())
            repository.add("Meeting old")
            repository.add("Meeting new")
            val newer = repository.getPage(0, 1).single()
            repository.setCompleted(newer.id, true)
            repeat(40) { repository.add("Unrelated $it") }
            assertEquals(listOf("Meeting new"), repository.getPage(0, 1, "meeting").map { it.title })
            assertEquals(listOf("Meeting old"), repository.getPage(1, 1, "meeting").map { it.title })
            assertEquals(listOf("Meeting old"), repository.getPage(0, 10, "meeting", TaskFilter.ACTIVE).map { it.title })
            assertEquals(listOf("Meeting new"), repository.getPage(0, 10, "meeting", TaskFilter.COMPLETED).map { it.title })
            repository.add("100% ready")
            repository.add("task_name")
            assertEquals(listOf("100% ready"), repository.getPage(0, 10, "%").map { it.title })
            assertEquals(listOf("task_name"), repository.getPage(0, 10, "_").map { it.title })
        } finally {
            database.close()
        }
    }
}
