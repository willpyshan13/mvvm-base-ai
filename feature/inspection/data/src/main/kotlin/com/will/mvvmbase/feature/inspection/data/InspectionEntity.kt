package com.will.mvvmbase.feature.inspection.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.will.mvvmbase.feature.inspection.domain.InspectionRecord

@Entity(tableName = "inspections")
data class InspectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val equipment: String,
    val location: String,
    val notes: String,
    val photoUri: String?,
    val checkedMask: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
) {
    fun toDomain() = InspectionRecord(id, equipment, location, notes, photoUri, checkedMask, createdAt, updatedAt, completedAt)
}
