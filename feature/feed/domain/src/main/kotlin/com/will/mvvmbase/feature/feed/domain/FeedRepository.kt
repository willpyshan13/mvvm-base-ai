package com.will.mvvmbase.feature.feed.domain

import kotlinx.coroutines.flow.Flow

interface FeedRepository {
    val items: Flow<List<FeedItem>>
    val isDemo: Boolean
    suspend fun refresh()
    suspend fun loadMore(): Boolean
    suspend fun add(title: String)
    suspend fun rename(id: String, title: String)
    suspend fun sync()
    suspend fun resolve(id: String, keepLocal: Boolean)
    suspend fun simulateConflict(id: String)
    fun simulateOffline(offline: Boolean)
}
