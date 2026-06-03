package com.liuguang.media.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "podcast_subscriptions",
    indices = [Index(value = ["url"], unique = true)]
)
data class PodcastSubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val description: String = "",
    val imageUrl: String = "",
    val link: String = "",
    val episodeCount: Int = 0,
    val lastRefreshTime: Long = 0,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val lastCheckStatus: String = "未检测",
    val lastCheckTime: Long = 0
)
