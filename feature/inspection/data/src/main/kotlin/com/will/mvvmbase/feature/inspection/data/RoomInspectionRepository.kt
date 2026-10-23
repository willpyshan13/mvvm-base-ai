package com.will.mvvmbase.feature.inspection.data

import com.will.mvvmbase.feature.inspection.domain.InspectionDraft
import com.will.mvvmbase.feature.inspection.domain.InspectionRecord
import com.will.mvvmbase.feature.inspection.domain.InspectionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomInspectionRepository @Inject constructor(private val dao: InspectionDao) : InspectionRepository {
    override fun observeAll(): Flow<List<InspectionRecord>> =
        dao.observeAll().map { rows -> rows.map(InspectionEntity::toDomain) }

    override fun observe(id: Long): Flow<InspectionRecord?> =
        dao.observe(id).map { it?.toDomain() }

    override suspend fun create(draft: InspectionDraft, complete: Boolean): Long {
        val value = draft.validated()
        if (complete) require(value.checkedMask == InspectionRecord.ALL_CHECKS) { "checks" }
        val now = System.currentTimeMillis()
        return dao.insert(InspectionEntity(
            equipment = value.equipment, location = value.location, notes = value.notes,
            photoUri = value.photoUri, checkedMask = value.checkedMask,
            createdAt = now, updatedAt = now, completedAt = if (complete) now else null,
        ))
    }

    override suspend fun updateDraft(id: Long, draft: InspectionDraft): Boolean = update(id, draft, false)

    override suspend fun complete(id: Long, draft: InspectionDraft): Boolean {
        require(draft.checkedMask == InspectionRecord.ALL_CHECKS) { "checks" }
        return update(id, draft, true)
    }

    private suspend fun update(id: Long, draft: InspectionDraft, complete: Boolean): Boolean {
        val value = draft.validated()
        val now = System.currentTimeMillis()
        return dao.updateOpen(
            id, value.equipment, value.location, value.notes, value.photoUri,
            value.checkedMask, now, if (complete) now else null,
        ) == 1
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}
