package com.liuguang.media.data.repository

import com.liuguang.media.data.local.dao.VideoSiteDao
import com.liuguang.media.data.local.entity.VideoSiteEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SiteRepository @Inject constructor(
    private val siteDao: VideoSiteDao
) {
    fun observeAllSites(): Flow<List<VideoSiteEntity>> = siteDao.observeAll()

    suspend fun getAllSites(): List<VideoSiteEntity> = siteDao.getAll()

    suspend fun getEnabledSites(): List<VideoSiteEntity> = siteDao.getEnabled()

    suspend fun insertSite(site: VideoSiteEntity): Long = siteDao.insert(site)

    suspend fun insertSites(sites: List<VideoSiteEntity>): List<Long> = siteDao.insertAll(sites)

    suspend fun updateSite(site: VideoSiteEntity) = siteDao.update(site)

    suspend fun updateSites(sites: List<VideoSiteEntity>) = siteDao.updateAll(sites)

    suspend fun setDefaultSite(siteId: Long) = siteDao.setDefault(siteId)

    suspend fun deleteSite(site: VideoSiteEntity) = siteDao.delete(site)

    suspend fun clearAllSites() = siteDao.clearAll()

    suspend fun resetToDefaults() {
        siteDao.clearAll()
    }

    suspend fun moveSiteUp(site: VideoSiteEntity, allSites: List<VideoSiteEntity>) {
        val currentIndex = allSites.indexOfFirst { it.id == site.id }
        if (currentIndex > 0) {
            val prevSite = allSites[currentIndex - 1]
            siteDao.update(site.copy(sortOrder = prevSite.sortOrder))
            siteDao.update(prevSite.copy(sortOrder = site.sortOrder))
        }
    }

    suspend fun moveSiteDown(site: VideoSiteEntity, allSites: List<VideoSiteEntity>) {
        val currentIndex = allSites.indexOfFirst { it.id == site.id }
        if (currentIndex < allSites.size - 1) {
            val nextSite = allSites[currentIndex + 1]
            siteDao.update(site.copy(sortOrder = nextSite.sortOrder))
            siteDao.update(nextSite.copy(sortOrder = site.sortOrder))
        }
    }
}
