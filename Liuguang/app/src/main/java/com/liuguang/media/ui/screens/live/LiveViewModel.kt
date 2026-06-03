package com.liuguang.media.ui.screens.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liuguang.media.data.repository.LiveRepository
import com.liuguang.media.domain.model.LiveChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class LiveUiState {
    object Loading : LiveUiState()
    data class Success(val channels: List<LiveChannel>) : LiveUiState()
    data class Error(val message: String) : LiveUiState()
    object Empty : LiveUiState()
}

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val liveRepository: LiveRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LiveUiState>(LiveUiState.Loading)
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()

    private val _currentSourceId = MutableStateFlow<Long?>(null)
    val currentSourceId: StateFlow<Long?> = _currentSourceId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    private var allChannels = emptyList<LiveChannel>()
    private var allGroups = emptyList<String>()
    private var loadChannelsJob: Job? = null
    private var filterJob: Job? = null

    val sources = liveRepository.observeAllSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            sources.collectLatest { sourceList ->
                val enabledSources = sourceList.filter { it.enabled }
                when {
                    enabledSources.isEmpty() -> {
                        loadChannelsJob?.cancel()
                        filterJob?.cancel()
                        _currentSourceId.value = null
                        allChannels = emptyList()
                        allGroups = emptyList()
                        _uiState.value = LiveUiState.Empty
                    }
                    _currentSourceId.value == null || enabledSources.none { it.id == _currentSourceId.value } -> {
                        selectSource(enabledSources.first().id)
                    }
                }
            }
        }
    }

    fun selectSource(sourceId: Long) {
        viewModelScope.launch {
            _currentSourceId.value = sourceId
            val source = sources.value.find { it.id == sourceId }
            if (source != null) {
                loadChannels(source.url)
            }
        }
    }

    private fun loadChannels(url: String) {
        loadChannelsJob?.cancel()
        loadChannelsJob = viewModelScope.launch {
            _uiState.value = LiveUiState.Loading
            liveRepository.fetchAndParseChannels(url).fold(
                onSuccess = { channels ->
                    allChannels = channels
                    allGroups = withContext(Dispatchers.Default) {
                        channels.map { it.group }.distinct().sorted()
                    }
                    scheduleApplyFilters()
                },
                onFailure = { error ->
                    _uiState.value = LiveUiState.Error(error.message ?: "加载失败")
                }
            )
        }
    }

    fun setSearchQuery(query: String) {
        if (_searchQuery.value == query) return
        _searchQuery.value = query
        scheduleApplyFilters(debounceMs = 180L)
    }

    fun showAllChannels() {
        if (_searchQuery.value.isBlank() && _selectedGroup.value == null) return
        _searchQuery.value = ""
        _selectedGroup.value = null
        scheduleApplyFilters()
    }

    fun selectGroup(group: String?) {
        if (_selectedGroup.value == group) return
        _selectedGroup.value = group
        scheduleApplyFilters()
    }

    private fun scheduleApplyFilters(debounceMs: Long = 0L) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            val query = _searchQuery.value
            val group = _selectedGroup.value
            val channels = allChannels

            val filtered = withContext(Dispatchers.Default) {
                channels.filter { channel ->
                    val matchesQuery = query.isBlank() || channel.name.contains(query, ignoreCase = true)
                    val matchesGroup = group == null || channel.group == group
                    matchesQuery && matchesGroup
                }
            }

            _uiState.value = if (filtered.isEmpty()) {
                LiveUiState.Empty
            } else {
                LiveUiState.Success(filtered)
            }
        }
    }

    fun getGroups(): List<String> {
        return allGroups
    }
}
