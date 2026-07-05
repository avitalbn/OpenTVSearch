package org.opentvsearch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import org.opentvsearch.core.search.ResultKind
import org.opentvsearch.core.search.SearchResult

/**
 * Compose-for-TV search UI: a D-pad-focusable query field with a mic button, a loading /
 * error line, and a [TvLazyVerticalGrid] of result cards. Clicking a card launches the owning
 * app via its [SearchResult.launch] intent (handled by the caller). The first result is
 * auto-focused so the remote lands on content immediately after a search.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoice: () -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onOpenSettings: () -> Unit,
    onLaunchApp: (packageName: String, withQuery: Boolean) -> Unit,
) {
    val firstItemFocus = remember { FocusRequester() }

    LaunchedEffect(state.results) {
        if (state.results.isNotEmpty()) {
            runCatching { firstItemFocus.requestFocus() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Overscan-safe margins (≈5%): 48dp sides / 27dp top-bottom on the 960×540 canvas.
            .padding(PaddingValues(horizontal = 48.dp, vertical = 27.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onSubmit) { Text("Search") }
            Button(onClick = onVoice) { Text("\uD83C\uDFA4 Voice") }
            Button(onClick = onOpenSettings) { Text("\u2699 Settings") }
        }

        when {
            state.isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Searching\u2026")
            }

            state.error != null ->
                Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)

            state.results.isEmpty() && state.query.isNotBlank() ->
                Text("No results for \"${state.query}\".")
        }

        // Idle (no query typed AND nothing to show) → the always-populated Discover home instead of
        // an empty void; any active/typed query switches back to the result grid unchanged.
        val showDiscover = state.query.isBlank() && state.results.isEmpty()
        if (showDiscover) {
            DiscoverRow(
                apps = state.contentApps,
                onLaunchApp = onLaunchApp,
            )
        } else {
            TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(2),
                state = rememberTvLazyGridState(),
                modifier = Modifier.fillMaxSize(),
                // Reserve room so the focused card (scaled up) is never clipped at the edges.
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    items = state.results,
                    key = { _, r -> "${r.sourceId}:${r.id}" },
                ) { index, result ->
                    ResultCard(
                        result = result,
                        onClick = { onResultClick(result) },
                        modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier,
                    )
                }
            }
        }
    }
}

/**
 * TV-native search field: a focusable [Surface] (so its colors resolve from the TV dark scheme)
 * wrapping a [BasicTextField]. Text color = `onSurface`, placeholder = `onSurfaceVariant`,
 * container = `surfaceVariant`, cursor = brand `primary`. A visible focused border tells the user
 * when the field has D-pad focus. This replaces the mobile `OutlinedTextField`, whose colors were
 * resolved from an unthemed mobile scheme inside the TV theme (making the text invisible).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)

    Surface(
        modifier = modifier,
        shape = shape,
        colors = SurfaceDefaults.colors(
            containerColor = colorScheme.surfaceVariant,
            contentColor = colorScheme.onSurface,
        ),
        border = if (isFocused) {
            Border(border = BorderStroke(2.dp, colorScheme.primary), shape = shape)
        } else {
            Border.None
        },
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = colorScheme.onSurface),
            cursorBrush = SolidColor(colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = "Search movies, shows, clips",
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ResultCard(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(12.dp)
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    // Load the owning app's icon (compact tiles are icon-led like the Discover rail). Remembered
    // per package; getApplicationIcon can throw if the package vanished, hence runCatching.
    val appIcon = remember(result.packageName) {
        result.packageName?.let {
            runCatching { context.packageManager.getApplicationIcon(it) }.getOrNull()
        }
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(cardShape),
        // Pin colors dark in every state so focus never flips to white-on-white; focus = scale+border.
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colorScheme.surfaceVariant,
            contentColor = colorScheme.onSurface,
            focusedContainerColor = colorScheme.surfaceVariant,
            focusedContentColor = colorScheme.onSurface,
            pressedContainerColor = colorScheme.surfaceVariant,
            pressedContentColor = colorScheme.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, colorScheme.primary),
                shape = cardShape,
            ),
        ),
    ) {
        // Compact HORIZONTAL tile: small artwork on the left, title + source on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    // INLINE rows may carry real poster art — prefer it.
                    result.posterUri != null -> AsyncImage(
                        model = result.posterUri,
                        contentDescription = result.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Otherwise show the owning app's icon (hand-off cards, iconful and branded).
                    appIcon != null -> AsyncImage(
                        model = appIcon,
                        contentDescription = result.sourceLabel,
                        modifier = Modifier.fillMaxSize(0.8f),
                    )
                    // Last resort: the source's first letter.
                    else -> Text(
                        text = result.sourceLabel.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    KindBadge(result.kind)
                    Text(
                        text = result.sourceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun KindBadge(kind: ResultKind) {
    val (label, bg) = when (kind) {
        ResultKind.INLINE -> "PLAY" to Color(0xFF1B5E20)
        ResultKind.HANDOFF -> "OPEN APP" to Color(0xFF37474F)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}
