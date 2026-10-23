package com.will.mvvmbase.feature.feed.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "feed_cursor")
data class FeedCursor(@PrimaryKey val key: Int = 0, val offset: Int = 0, val end: Boolean = false)
