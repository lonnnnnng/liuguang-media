package com.liuguang.media.data.repository

import com.liuguang.media.data.local.dao.HistoryDao
import com.liuguang.media.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao
) {
    fun observeAllHistory(): Flow<List<HistoryEntity>> = historyDao.observeAll()

    suspend fun upsertHistory(history: HistoryEntity) = historyDao.upsert(history)

    suspend fun clearAllHistory() = historyDao.clearAll()

    suspend fun recordPlayback(
        siteId: Long,
        vodId: String,
        vodName: String,
        vodPic: String,
        episodeLabel: String,
        episodeUrl: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val key = "$siteId-$vodId"
        val history = HistoryEntity(
            key = key,
            siteId = siteId,
            vodId = vodId,
            vodName = vodName,
            vodPic = vodPic,
            episodeLabel = episodeLabel,
            episodeUrl = episodeUrl,
            positionMs = positionMs,
            durationMs = durationMs,
            lastPlayTime = System.currentTimeMillis()
        )
        upsertHistory(history)
    }
}
