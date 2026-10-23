package com.will.mvvmbase.feature.inspection.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [InspectionEntity::class], version = 1, exportSchema = true)
abstract class InspectionDatabase : RoomDatabase() {
    abstract fun inspectionDao(): InspectionDao
}
