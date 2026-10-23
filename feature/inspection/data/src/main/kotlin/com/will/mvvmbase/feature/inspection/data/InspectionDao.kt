package com.will.mvvmbase.feature.inspection.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectionDao {
    @Query("SELECT * FROM inspections ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<InspectionEntity>>

    @Query("SELECT * FROM inspections WHERE id = :id")
    fun observe(id: Long): Flow<InspectionEntity?>

    @Insert
    suspend fun insert(entity: InspectionEntity): Long

    @Query("UPDATE inspections SET equipment = :equipment, location = :location, notes = :notes, photoUri = :photoUri, checkedMask = :checkedMask, updatedAt = :updatedAt, completedAt = :completedAt WHERE id = :id AND completedAt IS NULL")
    suspend fun updateOpen(id: Long, equipment: String, location: String, notes: String, photoUri: String?, checkedMask: Int, updatedAt: Long, completedAt: Long?): Int

    @Query("DELETE FROM inspections WHERE id = :id")
    suspend fun delete(id: Long)
}
