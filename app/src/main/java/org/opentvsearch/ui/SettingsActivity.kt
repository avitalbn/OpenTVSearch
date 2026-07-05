package org.opentvsearch.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.opentvsearch.ui.theme.OpenTvSearchTheme

/**
 * Non-exported settings screen, launched from [SearchScreen]'s "Settings" button via an Intent.
 * Hosts the Compose-for-TV [SettingsScreen] and wires [SettingsViewModel] via Hilt. Changes are
 * written straight through to the DataStore-backed SettingsRepository and take effect on the next
 * search (the source list is rebuilt per query).
 */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenTvSearchTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = state,
                    onVoiceOnLaunchChange = viewModel::setVoiceOnLaunch,
                    onSourceEnabledChange = viewModel::setSourceEnabled,
                    onMoveSource = viewModel::moveSource,
                )
            }
        }
    }
}
