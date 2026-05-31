package com.liuguang.media.ui.screens.search

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    init {
        loadSearchHistory()
    }

    private fun loadSearchHistory() {
        _searchHistory.value = prefs.getString(KEYWORDS_KEY, "")
            .orEmpty()
            .split(KEYWORDS_SEPARATOR)
            .filter { it.isNotBlank() }
    }

    fun addSearchHistory(keyword: String) {
        val history = _searchHistory.value.toMutableList()
        history.remove(keyword)
        history.add(0, keyword)
        if (history.size > 10) {
            history.removeAt(history.size - 1)
        }
        _searchHistory.value = history
        prefs.edit()
            .putString(KEYWORDS_KEY, history.joinToString(KEYWORDS_SEPARATOR))
            .apply()
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        prefs.edit().remove(KEYWORDS_KEY).apply()
    }

    private companion object {
        const val KEYWORDS_KEY = "keywords"
        const val KEYWORDS_SEPARATOR = "\u001F"
    }
}
