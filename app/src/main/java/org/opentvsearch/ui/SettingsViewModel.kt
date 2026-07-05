package org.opentvsearch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opentvsearch.core.apps.InstalledAppDetector
import org.opentvsearch.core.search.SourceDescriptor
import org.opentvsearch.core.settings.SettingsRepository
import org.opentvsearch.core.sources.SourceCatalog
import javax.inject.Inject

/**
 * Drives SettingsActivity. Exposes the voice-on-launch toggle plus the ORDERED, enable-aware
 * source catalog ([SettingsUiState.sources]), and writes every change straight through to
 * [SettingsRepository] (which the live search reads on the next query).
 *
 * The installed-package set is read once (off the main thread) and combined with the three
 * settings flows through the pure [SourceCatalog.buildSourceCatalog] mapping, so the UI updates
 * reactively as the user toggles/reorders sources.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val detector: InstalledAppDetector,
) : ViewModel() {

    private val installedPackages = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<SettingsUiState> =
        combine(
            settings.voiceOnLaunch,
            settings.disabledSourceIds,
            settings.sourceOrder,
            installedPackages,
        ) { voiceOnLaunch, disabled, order, installed ->
            SettingsUiState(
                voiceOnLaunch = voiceOnLaunch,
                sources = SourceCatalog.buildSourceCatalog(
                    recommended = InstalledAppDetector.RECOMMENDED,
                    installedPackages = installed,
                    disabledIds = disabled,
                    order = order,
                ),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    init {
        viewModelScope.launch {
            installedPackages.value = withContext(Dispatchers.IO) {
                runCatching { detector.installedLeanbackPackages() }
                    .getOrDefault(emptyList())
                    .toSet()
            }
        }
    }

    fun setVoiceOnLaunch(enabled: Boolean) {
        viewModelScope.launch { settings.setVoiceOnLaunch(enabled) }
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { settings.setSourceEnabled(id, enabled) }
    }

    /** Move a source one slot up ([up] = true) or down, persisting the resulting full order. */
    fun moveSource(id: String, up: Boolean) {
        val currentIds = state.value.sources.map { it.id }
        val reordered = SourceCatalog.moveOrder(currentIds, id, up)
        if (reordered == currentIds) return
        viewModelScope.launch { settings.setSourceOrder(reordered) }
    }
}

data class SettingsUiState(
    val voiceOnLaunch: Boolean = false,
    val sources: List<SourceDescriptor> = emptyList(),
)
