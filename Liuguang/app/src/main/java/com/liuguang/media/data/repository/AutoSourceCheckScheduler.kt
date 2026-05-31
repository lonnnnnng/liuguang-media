package com.liuguang.media.data.repository

import com.liuguang.media.data.local.entity.VideoSiteEntity
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
class AutoSourceCheckScheduler @Inject constructor(
    private val networkSettingsRepository: NetworkSettingsRepository,
    private val siteRepository: SiteRepository,
    private val vodRepository: VodRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            networkSettingsRepository.settings.collectLatest { settings ->
                val interval = settings.autoCheckIntervalMinutes
                if (interval == null) {
                    return@collectLatest
                }
                while (true) {
                    delay(interval * 60_000L)
                    checkVideoSources(settings.videoSourceTimeoutMs)
                }
            }
        }
    }

    private suspend fun checkVideoSources(timeoutMs: Long) {
        val checkedSites = siteRepository.getAllSites().map { site ->
            checkSite(site, timeoutMs)
        }
        if (checkedSites.isNotEmpty()) {
            siteRepository.updateSites(reorderCheckedSites(checkedSites))
        }
    }

    private suspend fun checkSite(
        site: VideoSiteEntity,
        timeoutMs: Long
    ): VideoSiteEntity {
        return vodRepository.checkVideoSite(site.apiUrl, timeoutMs = timeoutMs).fold(
            onSuccess = { response ->
                site.copy(
                    enabled = true,
                    lastCheckStatus = "可用",
                    lastCheckTime = System.currentTimeMillis(),
                    lastLatencyMs = response.latencyMs
                )
            },
            onFailure = { error ->
                site.copy(
                    enabled = false,
                    lastCheckStatus = classifySourceCheckFailure(error).statusText,
                    lastCheckTime = System.currentTimeMillis(),
                    lastLatencyMs = 0,
                    isDefault = false
                )
            }
        )
    }

    private fun reorderCheckedSites(sites: List<VideoSiteEntity>): List<VideoSiteEntity> {
        val availableSites = sites
            .filter { it.enabled && it.lastLatencyMs > 0L }
            .sortedWith(compareBy<VideoSiteEntity> { it.lastLatencyMs }.thenBy { it.id })
        val unavailableSites = sites
            .filterNot { it.enabled && it.lastLatencyMs > 0L }
            .sortedWith(compareBy<VideoSiteEntity> { it.sortOrder }.thenBy { it.id })
        val defaultSiteId = availableSites.firstOrNull()?.id

        return (availableSites + unavailableSites).mapIndexed { index, site ->
            site.copy(
                sortOrder = index + 1,
                isDefault = site.id == defaultSiteId
            )
        }
    }
}
