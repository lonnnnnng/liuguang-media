package com.liuguang.media.data.local.dao

import androidx.room.*
import com.liuguang.media.data.local.entity.VideoSiteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoSiteDao {
    @Query("SELECT * FROM video_sites ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<VideoSiteEntity>>

    @Query("SELECT * FROM video_sites WHERE enabled = 1 ORDER BY isDefault DESC, sortOrder ASC, id ASC")
    suspend fun getEnabled(): List<VideoSiteEntity>

    @Query("SELECT * FROM video_sites ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<VideoSiteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(site: VideoSiteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sites: List<VideoSiteEntity>): List<Long>

    @Update
    suspend fun update(site: VideoSiteEntity)

    @Update
    suspend fun updateAll(sites: List<VideoSiteEntity>)

    @Query("UPDATE video_sites SET isDefault = CASE WHEN id = :siteId THEN 1 ELSE 0 END, enabled = CASE WHEN id = :siteId THEN 1 ELSE enabled END")
    suspend fun setDefault(siteId: Long)

    @Delete
    suspend fun delete(site: VideoSiteEntity)

    @Query("DELETE FROM video_sites")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM video_sites")
    suspend fun count(): Int
}
