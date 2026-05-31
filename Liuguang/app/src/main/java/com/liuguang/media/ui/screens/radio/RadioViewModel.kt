package com.liuguang.media.ui.screens.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liuguang.media.data.repository.RadioRepository
import com.liuguang.media.domain.model.RadioStation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class RadioUiState {
    object Loading : RadioUiState()
    data class Success(val stations: List<RadioStation>) : RadioUiState()
    data class Error(val message: String) : RadioUiState()
    object Empty : RadioUiState()
}

@HiltViewModel
class RadioViewModel @Inject constructor(
    private val radioRepository: RadioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RadioUiState>(RadioUiState.Loading)
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()

    private val _currentSourceId = MutableStateFlow<Long?>(null)
    val currentSourceId: StateFlow<Long?> = _currentSourceId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    private val _groups = MutableStateFlow<List<String>>(emptyList())
    val groups: StateFlow<List<String>> = _groups.asStateFlow()

    val sources = radioRepository.observeAllSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var allStations = emptyList<RadioStation>()
    private var loadStationsJob: Job? = null
    private var filterJob: Job? = null

    init {
        viewModelScope.launch {
            sources.collectLatest { sourceList ->
                val enabledSources = sourceList.filter { it.enabled }
                if (enabledSources.isNotEmpty() && _currentSourceId.value == null) {
                    selectSource(enabledSources.first().id)
                } else if (enabledSources.isEmpty()) {
                    allStations = emptyList()
                    _groups.value = emptyList()
                    _uiState.value = RadioUiState.Empty
                }
            }
        }
    }

    fun selectSource(sourceId: Long) {
        viewModelScope.launch {
            _currentSourceId.value = sourceId
            val source = sources.value.firstOrNull { it.id == sourceId } ?: return@launch
            loadStations(source.url)
        }
    }

    fun setSearchQuery(query: String) {
        if (_searchQuery.value == query) return
        _searchQuery.value = query
        scheduleApplyFilters(debounceMs = 180L)
    }

    fun showAllStations() {
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

    fun refreshCurrentSource() {
        val source = sources.value.firstOrNull { it.id == _currentSourceId.value } ?: return
        loadStations(source.url, forceRefresh = true)
    }

    private fun loadStations(url: String, forceRefresh: Boolean = false) {
        loadStationsJob?.cancel()
        loadStationsJob = viewModelScope.launch {
            _uiState.value = RadioUiState.Loading
            radioRepository.fetchAndParseStations(url, forceRefresh = forceRefresh).fold(
                onSuccess = { stations ->
                    allStations = stations
                    _groups.value = withContext(Dispatchers.Default) {
                        stations
                            .map { it.group.ifBlank { "默认" } }
                            .distinct()
                            .sorted()
                    }
                    scheduleApplyFilters()
                },
                onFailure = { error ->
                    _uiState.value = RadioUiState.Error(error.message ?: "加载失败")
                }
            )
        }
    }

    private fun scheduleApplyFilters(debounceMs: Long = 0L) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            val query = _searchQuery.value.trim()
            val group = _selectedGroup.value
            val stations = allStations

            val filtered = withContext(Dispatchers.Default) {
                stations.filter { station ->
                    val matchesQuery = query.isBlank() ||
                        station.name.contains(query, ignoreCase = true) ||
                        station.country.contains(query, ignoreCase = true) ||
                        station.group.contains(query, ignoreCase = true)
                    val matchesGroup = group == null || station.group == group
                    matchesQuery && matchesGroup
                }
            }

            _uiState.value = if (filtered.isEmpty()) {
                RadioUiState.Empty
            } else {
                RadioUiState.Success(filtered)
            }
        }
    }
}
