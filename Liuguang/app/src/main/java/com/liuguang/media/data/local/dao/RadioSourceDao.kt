package com.liuguang.media.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.liuguang.media.data.local.entity.RadioSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioSourceDao {
    @Query("SELECT * FROM radio_sources ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<RadioSourceEntity>>

    @Query("SELECT * FROM radio_sources WHERE enabled = 1 ORDER BY sortOrder ASC, id ASC")
    suspend fun getEnabled(): List<RadioSourceEntity>

    @Query("SELECT * FROM radio_sources ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<RadioSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: RadioSourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<RadioSourceEntity>): List<Long>

    @Update
    suspend fun update(source: RadioSourceEntity)

    @Update
    suspend fun updateAll(sources: List<RadioSourceEntity>)

    @Delete
    suspend fun delete(source: RadioSourceEntity)

    @Query("DELETE FROM radio_sources")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM radio_sources")
    suspend fun count(): Int
}
