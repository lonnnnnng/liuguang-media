package com.liuguang.media.data.local.dao

import androidx.room.*
import com.liuguang.media.data.local.entity.LiveSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveSourceDao {
    @Query("SELECT * FROM live_sources ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<LiveSourceEntity>>

    @Query("SELECT * FROM live_sources WHERE enabled = 1 ORDER BY sortOrder ASC, id ASC")
    suspend fun getEnabled(): List<LiveSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: LiveSourceEntity): Long

    @Update
    suspend fun update(source: LiveSourceEntity)

    @Delete
    suspend fun delete(source: LiveSourceEntity)

    @Query("DELETE FROM live_sources")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM live_sources")
    suspend fun count(): Int
}
