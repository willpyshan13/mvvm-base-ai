package com.will.mvvmbase.feature.inspection.domain

import kotlinx.coroutines.flow.Flow

interface InspectionRepository {
    fun observeAll(): Flow<List<InspectionRecord>>
    fun observe(id: Long): Flow<InspectionRecord?>
    suspend fun create(draft: InspectionDraft, complete: Boolean): Long
    suspend fun updateDraft(id: Long, draft: InspectionDraft): Boolean
    suspend fun complete(id: Long, draft: InspectionDraft): Boolean
    suspend fun delete(id: Long)
}
