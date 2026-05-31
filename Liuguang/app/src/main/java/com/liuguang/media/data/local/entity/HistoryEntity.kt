package com.liuguang.media.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "histories")
data class HistoryEntity(
    @PrimaryKey
    val key: String, // format: "siteId-vodId"
    val siteId: Long,
    val vodId: String,
    val vodName: String,
    val vodPic: String,
    val episodeLabel: String,
    val episodeUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayTime: Long = System.currentTimeMillis()
)
