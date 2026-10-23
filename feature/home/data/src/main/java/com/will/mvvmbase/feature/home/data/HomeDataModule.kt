package com.will.mvvmbase.feature.home.data

import android.content.Context
import androidx.room.Room
import com.will.mvvmbase.core.apm.AppApm
import com.will.mvvmbase.feature.home.domain.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeRepositoryBindings {
    @Binds
    @Singleton
    abstract fun bindTaskRepository(implementation: RoomTaskRepository): TaskRepository
}

@Module
@InstallIn(SingletonComponent::class)
object HomeDataModule {
    @Provides
    @Singleton
    fun provideTaskDatabase(@ApplicationContext context: Context): TaskDatabase =
        Room.databaseBuilder(context, TaskDatabase::class.java, "tasks.db")
            .addMigrations(TaskDatabase.MIGRATION_1_2)
            .setQueryCallback(AppApm.roomQueryCallback(), AppApm.roomQueryExecutor())
            .build()

    @Provides
    fun provideTaskDao(database: TaskDatabase): TaskDao = database.taskDao()
}
