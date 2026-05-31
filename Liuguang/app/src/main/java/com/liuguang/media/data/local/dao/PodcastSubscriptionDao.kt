package com.liuguang.media.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.liuguang.media.data.local.entity.PodcastSubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastSubscriptionDao {
    @Query("SELECT * FROM podcast_subscriptions ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<PodcastSubscriptionEntity>>

    @Query("SELECT * FROM podcast_subscriptions ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<PodcastSubscriptionEntity>

    @Query("SELECT * FROM podcast_subscriptions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PodcastSubscriptionEntity?

    @Query("SELECT * FROM podcast_subscriptions WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): PodcastSubscriptionEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM podcast_subscriptions")
    suspend fun maxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: PodcastSubscriptionEntity): Long

    @Update
    suspend fun update(subscription: PodcastSubscriptionEntity)

    @Delete
    suspend fun delete(subscription: PodcastSubscriptionEntity)
}
