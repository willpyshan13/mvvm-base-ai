package com.will.mvvmbase.feature.inspection.domain

data class InspectionRecord(
    val id: Long,
    val equipment: String,
    val location: String,
    val notes: String,
    val photoUri: String?,
    val checkedMask: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
) {
    val isCompleted: Boolean get() = completedAt != null
    val checkedCount: Int get() = Integer.bitCount(checkedMask and ALL_CHECKS)

    companion object {
        const val ALL_CHECKS = 0b111
    }
}

data class InspectionDraft(
    val equipment: String,
    val location: String,
    val notes: String,
    val photoUri: String?,
    val checkedMask: Int,
) {
    fun validated(): InspectionDraft {
        val cleanEquipment = equipment.trim()
        val cleanLocation = location.trim()
        require(cleanEquipment.isNotEmpty() && cleanEquipment.length <= 80) { "equipment" }
        require(cleanLocation.isNotEmpty() && cleanLocation.length <= 120) { "location" }
        require(notes.length <= 2000) { "notes" }
        require(checkedMask and InspectionRecord.ALL_CHECKS == checkedMask) { "checkedMask" }
        return copy(equipment = cleanEquipment, location = cleanLocation, notes = notes.trim())
    }
}
