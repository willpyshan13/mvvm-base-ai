package com.will.mvvmbase.feature.feed.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM feed ORDER BY id") fun observe(): Flow<List<FeedRow>>
    @Query("SELECT * FROM feed WHERE id = :id") suspend fun find(id: String): FeedRow?
    @Query("SELECT * FROM feed WHERE pending = 1 AND conflict = 0") suspend fun pending(): List<FeedRow>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(row: FeedRow)
    @Query("DELETE FROM feed WHERE pending = 0") suspend fun clearClean()
    @Query("SELECT * FROM feed_cursor WHERE `key` = 0") suspend fun cursor(): FeedCursor?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun cursor(cursor: FeedCursor)
}
