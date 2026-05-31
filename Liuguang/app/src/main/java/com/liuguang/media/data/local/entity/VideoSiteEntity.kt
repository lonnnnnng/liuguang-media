package com.liuguang.media.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_sites")
data class VideoSiteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val apiUrl: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val lastCheckStatus: String = "未检测",
    val lastCheckTime: Long = 0,
    val lastLatencyMs: Long = 0,
    val isDefault: Boolean = false
)
