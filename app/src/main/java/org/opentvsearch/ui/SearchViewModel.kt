package org.opentvsearch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.opentvsearch.core.apps.ContentAppRepository
import org.opentvsearch.core.apps.ContentAppTile
import org.opentvsearch.core.search.SearchAggregator
import org.opentvsearch.core.search.SearchResult
import org.opentvsearch.core.settings.SettingsRepository
import javax.inject.Inject

/**
 * Drives SearchActivity: holds the query, fans it out through [SearchAggregator],
 * and exposes merged results. Unlike ARVIO's single-TMDB-call ViewModel, results
 * here come from every enabled SearchSource.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val aggregator: SearchAggregator,
    private val settings: SettingsRepository,
    private val contentApps: ContentAppRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val apps = contentApps.loadContentApps()
            _state.value = _state.value.copy(contentApps = apps)
        }
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    /** Reads the persisted "fire voice recognizer on launch" toggle once. */
    suspend fun voiceOnLaunch(): Boolean = settings.voiceOnLaunch.first()

    /** Seed the query from a voice recognition result and immediately search. */
    fun onVoiceResult(text: String) {
        onQueryChange(text)
        submit()
    }

    fun submit() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            runCatching { aggregator.search(q) }
                .onSuccess { _state.value = _state.value.copy(isLoading = false, results = it) }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
        }
    }
}

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val error: String? = null,
    val contentApps: List<ContentAppTile> = emptyList(),
)
