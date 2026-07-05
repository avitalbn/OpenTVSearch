package org.opentvsearch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.opentvsearch.core.search.SourceCapability
import org.opentvsearch.core.search.SourceDescriptor

/**
 * Compose-for-TV settings screen: a voice-on-launch toggle and a D-pad-navigable source list
 * (recommended+installed apps pinned at top). Every color resolves from [OpenTvSearchTheme]; the
 * interactive rows pin container/content colors dark for ALL focus states (like the result cards)
 * and signal focus with a brand-colored border, so there is never white-on-white.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onVoiceOnLaunchChange: (Boolean) -> Unit,
    onSourceEnabledChange: (String, Boolean) -> Unit,
    onMoveSource: (String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Overscan-safe margins (≈5%), matching SearchScreen.
            .padding(PaddingValues(horizontal = 48.dp, vertical = 27.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        ToggleRow(
            label = "Voice search on launch",
            hint = "Open the voice recognizer as soon as the app starts",
            enabled = state.voiceOnLaunch,
            onToggle = { onVoiceOnLaunchChange(!state.voiceOnLaunch) },
        )

        Text(
            text = "Sources",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Turn sources on/off and reorder them. Recommended apps are pinned to the top.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TvLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = state.sources, key = { it.id }) { descriptor ->
                SourceRow(
                    descriptor = descriptor,
                    onToggle = { onSourceEnabledChange(descriptor.id, !descriptor.enabled) },
                    onMoveUp = { onMoveSource(descriptor.id, true) },
                    onMoveDown = { onMoveSource(descriptor.id, false) },
                )
            }
        }
    }
}

/**
 * A focusable, D-pad-clickable settings row that toggles a boolean. Rendered as a [Surface] with
 * pinned dark colors (never white-on-white) and a focus border; the current state is shown as an
 * "On"/"Off" chip on the trailing edge.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ToggleRow(
    label: String,
    hint: String,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colorScheme.surface,
            contentColor = colorScheme.onSurface,
            focusedContainerColor = colorScheme.surface,
            focusedContentColor = colorScheme.onSurface,
            pressedContainerColor = colorScheme.surface,
            pressedContentColor = colorScheme.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, colorScheme.primary), shape = shape),
        ),
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            StateChip(on = enabled, focused = isFocused)
        }
    }
}

/**
 * A source row: label + capability hint on the left, an enable/disable toggle chip, and
 * move-up/move-down buttons (D-pad friendly — no drag required on a remote).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceRow(
    descriptor: SourceDescriptor,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val capabilityHint = when (descriptor.capability) {
        SourceCapability.INLINE_RESULTS -> "Searches inside app"
        SourceCapability.HANDOFF_ONLY -> "Opens app"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = androidx.tv.material3.SurfaceDefaults.colors(
            containerColor = colorScheme.surface,
            contentColor = colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = descriptor.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = if (descriptor.recommended) "Recommended \u00B7 $capabilityHint"
                    else capabilityHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Button(onClick = onToggle) {
                Text(if (descriptor.enabled) "On" else "Off")
            }
            Button(onClick = onMoveUp) { Text("\u25B2") }
            Button(onClick = onMoveDown) { Text("\u25BC") }
        }
    }
}

/** Small "On"/"Off" indicator. Colors resolve from the theme (primary when on, variant when off). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StateChip(on: Boolean, focused: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    val container = if (on) colorScheme.primary else colorScheme.surfaceVariant
    val content = if (on) colorScheme.onPrimary else colorScheme.onSurfaceVariant
    Surface(
        colors = androidx.tv.material3.SurfaceDefaults.colors(
            containerColor = container,
            contentColor = content,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = if (on) "On" else "Off",
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
