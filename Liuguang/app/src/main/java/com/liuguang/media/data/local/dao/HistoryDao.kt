package com.liuguang.media.data.local.dao

import androidx.room.*
import com.liuguang.media.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM histories ORDER BY lastPlayTime DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: HistoryEntity)

    @Query("DELETE FROM histories")
    suspend fun clearAll()
}
