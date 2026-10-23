package com.will.mvvmbase.feature.inspection.data

import android.content.Context
import androidx.room.Room
import com.will.mvvmbase.core.apm.AppApm
import com.will.mvvmbase.feature.inspection.domain.InspectionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InspectionRepositoryBindings {
    @Binds
    @Singleton
    abstract fun bind(implementation: RoomInspectionRepository): InspectionRepository
}

@Module
@InstallIn(SingletonComponent::class)
object InspectionDataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): InspectionDatabase =
        Room.databaseBuilder(context, InspectionDatabase::class.java, "inspections.db")
            .setQueryCallback(AppApm.roomQueryCallback(), AppApm.roomQueryExecutor())
            .build()

    @Provides
    fun dao(database: InspectionDatabase): InspectionDao = database.inspectionDao()
}
