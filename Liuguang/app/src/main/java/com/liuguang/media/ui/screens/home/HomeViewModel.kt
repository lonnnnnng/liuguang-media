package com.liuguang.media.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liuguang.media.data.local.entity.VideoSiteEntity
import com.liuguang.media.data.remote.VodClass
import com.liuguang.media.data.remote.VodItem
import com.liuguang.media.data.repository.SiteRepository
import com.liuguang.media.data.repository.VodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeVodSource(
    val siteId: Long,
    val siteName: String,
    val vod: VodItem
) {
    val key: String = "$siteId:${vod.vod_id}"
}

data class HomeVodItem(
    val groupKey: String,
    val sources: List<HomeVodSource>
) {
    val primary: HomeVodSource = sources.first()
    val siteId: Long = primary.siteId
    val siteName: String = primary.siteName
    val vod: VodItem = primary.vod
    val sourceCount: Int = sources.map { it.siteId }.distinct().size
    val sourceNames: List<String> = sources.map { it.siteName }.distinct()
    val key: String = groupKey
}

data class HomeCategory(
    val id: Int,
    val name: String
)

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val vodList: List<HomeVodItem>,
        val hasMore: Boolean,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val isAggregating: Boolean = false,
        val warningMessage: String? = null
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
    object Empty : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val siteRepository: SiteRepository,
    private val vodRepository: VodRepository
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 20
    }

    private data class SitePageResult(
        val site: VideoSiteEntity,
        val page: Int,
        val items: List<HomeVodSource>,
        val categories: List<HomeCategory>,
        val hasMore: Boolean,
        val error: Throwable?
    )

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _categories = MutableStateFlow<List<HomeCategory>>(emptyList())
    val categories: StateFlow<List<HomeCategory>> = _categories.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<Int?>(null)
    val selectedCategoryId: StateFlow<Int?> = _selectedCategoryId.asStateFlow()

    private var isLoadingVodList = false
    private var homeSiteSnapshot: VideoSiteEntity? = null
    private var homeSiteSignature: String? = null
    private var nextPage = 1
    private var hasMore = true
    private var hasStartedInitialLoad = false
    private var loadCategoriesJob: Job? = null

    init {
        viewModelScope.launch {
            siteRepository.observeAllSites().collectLatest { siteList ->
                val homeSite = selectHomeSite(siteList)
                val signature = homeSite?.let {
                    "${it.id}:${it.name}:${it.apiUrl}:${it.sortOrder}:${it.isDefault}"
                } ?: "none"
                if (signature != homeSiteSignature) {
                    homeSiteSignature = signature
                    homeSiteSnapshot = homeSite
                    loadCategoriesJob?.cancel()
                    _selectedCategoryId.value = null
                    _categories.value = emptyList()
                    resetPaging()
                    _uiState.value = HomeUiState.Loading
                    if (hasStartedInitialLoad) {
                        loadVodList(isRefresh = true)
                    }
                }
            }
        }
    }

    fun startInitialLoad() {
        if (hasStartedInitialLoad) return
        hasStartedInitialLoad = true
        loadVodList(isRefresh = true)
    }

    fun loadVodList(isRefresh: Boolean = false) {
        val previousState = _uiState.value
        if (isLoadingVodList) return
        if (!isRefresh) {
            val successState = previousState as? HomeUiState.Success ?: return
            if (!successState.hasMore || successState.isLoadingMore || successState.isRefreshing) return
        }

        isLoadingVodList = true
        viewModelScope.launch {
            try {
                val homeSite = homeSiteSnapshot ?: siteRepository.getEnabledSites()
                    .firstOrNull()
                    ?.also { homeSiteSnapshot = it }

                if (homeSite == null) {
                    _uiState.value = HomeUiState.Empty
                    return@launch
                }

                scheduleLoadCategories(
                    homeSite = homeSite,
                    forceRefresh = isRefresh
                )

                if (isRefresh) {
                    resetPaging()
                    _uiState.value = when (previousState) {
                        is HomeUiState.Success -> previousState.copy(
                            isRefreshing = true,
                            isLoadingMore = false,
                            warningMessage = null
                        )
                        else -> HomeUiState.Loading
                    }
                } else {
                    val successState = previousState as? HomeUiState.Success
                    if (successState != null) {
                        _uiState.value = successState.copy(isLoadingMore = true)
                    }
                }

                loadHomeSitePage(
                    homeSite = homeSite,
                    isRefresh = isRefresh,
                    previousState = previousState
                )
            } finally {
                isLoadingVodList = false
            }
        }
    }

    fun loadMore() {
        loadVodList(isRefresh = false)
    }

    fun refresh() {
        loadVodList(isRefresh = true)
    }

    fun selectCategory(categoryId: Int?) {
        if (_selectedCategoryId.value == categoryId) return
        _selectedCategoryId.value = categoryId
        resetPaging()
        loadVodList(isRefresh = true)
    }

    private suspend fun loadHomeSitePage(
        homeSite: VideoSiteEntity,
        isRefresh: Boolean,
        previousState: HomeUiState
    ) {
        val existingSources = if (isRefresh) {
            emptyList()
        } else {
            (previousState as? HomeUiState.Success)?.vodList
                .orEmpty()
                .flatMap { it.sources }
        }

        val result = loadSitePage(
            site = homeSite,
            page = if (isRefresh) 1 else nextPage,
            typeId = _selectedCategoryId.value,
            forceRefresh = isRefresh
        )

        if (result.error == null) {
            nextPage = result.page + 1
            hasMore = result.hasMore
            if (result.categories.isNotEmpty() && _categories.value.isEmpty()) {
                _categories.value = result.categories
            }
        } else {
            hasMore = false
        }

        val combinedList = if (result.error == null) {
            toHomeVodItems(existingSources + result.items)
        } else {
            toHomeVodItems(existingSources)
        }

        _uiState.value = when {
            result.error == null && combinedList.isNotEmpty() -> {
                HomeUiState.Success(
                    vodList = combinedList,
                    hasMore = hasMore,
                    isRefreshing = false,
                    isLoadingMore = false,
                    isAggregating = false,
                    warningMessage = null
                )
            }
            result.error == null -> HomeUiState.Empty
            previousState is HomeUiState.Success -> {
                val message = if (isRefresh) "刷新失败，已保留当前列表" else "加载失败，已保留当前列表"
                previousState.copy(
                    hasMore = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    isAggregating = false,
                    warningMessage = message
                )
            }
            else -> HomeUiState.Error(result.error.message ?: "默认视频源加载失败")
        }
    }

    private suspend fun loadSitePage(
        site: VideoSiteEntity,
        page: Int,
        typeId: Int?,
        forceRefresh: Boolean
    ): SitePageResult {
        return vodRepository.getVodList(
            baseUrl = site.apiUrl,
            page = page,
            typeId = typeId,
            forceRefresh = forceRefresh
        ).fold(
            onSuccess = { response ->
                val items = response.list.orEmpty().map { vod ->
                    HomeVodSource(
                        siteId = site.id,
                        siteName = site.name,
                        vod = vod
                    )
                }
                val responsePage = response.page ?: page
                val hasMore = items.isNotEmpty() && (
                    response.pagecount?.let { responsePage < it }
                        ?: (items.size >= PAGE_SIZE)
                    )
                SitePageResult(
                    site = site,
                    page = page,
                    items = items,
                    categories = response.toHomeCategories(),
                    hasMore = hasMore,
                    error = null
                )
            },
            onFailure = { error ->
                SitePageResult(
                    site = site,
                    page = page,
                    items = emptyList(),
                    categories = emptyList(),
                    hasMore = false,
                    error = error
                )
            }
        )
    }

    private fun scheduleLoadCategories(
        homeSite: VideoSiteEntity,
        forceRefresh: Boolean
    ) {
        if (!forceRefresh && _categories.value.isNotEmpty()) return
        if (loadCategoriesJob?.isActive == true) return
        loadCategoriesJob = viewModelScope.launch {
            vodRepository.getCategories(homeSite.apiUrl).onSuccess { response ->
                val categories = response.toHomeCategories()
                if (categories.isNotEmpty() && homeSiteSnapshot?.id == homeSite.id) {
                    _categories.value = categories
                }
            }
        }
    }

    private fun toHomeVodItems(sources: List<HomeVodSource>): List<HomeVodItem> {
        return sources.distinctBy { it.key }.map { source ->
            HomeVodItem(groupKey = source.key, sources = listOf(source))
        }
    }

    private fun selectHomeSite(sites: List<VideoSiteEntity>): VideoSiteEntity? {
        return sites
            .filter { it.enabled }
            .sortedWith(compareByDescending<VideoSiteEntity> { it.isDefault }.thenBy { it.sortOrder }.thenBy { it.id })
            .firstOrNull()
    }

    private fun resetPaging() {
        nextPage = 1
        hasMore = true
    }

    private fun List<VodClass>.toHomeCategories(): List<HomeCategory> {
        return mapNotNull { vodClass ->
            val name = vodClass.type_name.trim()
            if (name.isBlank()) return@mapNotNull null
            HomeCategory(id = vodClass.type_id, name = name)
        }.distinctBy { it.id }
    }

    private fun com.liuguang.media.data.remote.VodApiResponse.toHomeCategories(): List<HomeCategory> {
        return `class`.orEmpty().toHomeCategories()
            .ifEmpty { list.orEmpty().vodItemsToHomeCategories() }
    }

    private fun List<VodItem>.vodItemsToHomeCategories(): List<HomeCategory> {
        return mapNotNull { vod ->
            val id = vod.type_id ?: return@mapNotNull null
            val name = vod.type_name?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            HomeCategory(id = id, name = name)
        }.distinctBy { it.id }
    }
}
