package com.will.mvvmbase.feature.auth.domain

/** Feature-owned cleanup hook invoked after an authenticated session ends. */
interface SessionDataCleaner {
    suspend fun clearSessionData()
}
