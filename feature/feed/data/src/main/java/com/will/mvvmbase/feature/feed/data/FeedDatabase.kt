package com.will.mvvmbase.feature.feed.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Database(entities = [FeedRow::class, FeedCursor::class], version = 1, exportSchema = true)
abstract class FeedDatabase : RoomDatabase() { abstract fun feedDao(): FeedDao }
