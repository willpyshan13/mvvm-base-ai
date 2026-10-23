package com.will.mvvmbase.feature.feed.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "feed")
data class FeedRow(@PrimaryKey val id: String, val title: String, val version: Long,
    val pending: Boolean = false, val conflict: Boolean = false,
    val remoteTitle: String? = null, val remoteVersion: Long? = null)
