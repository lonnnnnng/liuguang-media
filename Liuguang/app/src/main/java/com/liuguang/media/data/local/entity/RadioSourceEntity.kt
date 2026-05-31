package com.liuguang.media.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "radio_sources")
data class RadioSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val lastCheckStatus: String = "未检测",
    val lastCheckTime: Long = 0
)
