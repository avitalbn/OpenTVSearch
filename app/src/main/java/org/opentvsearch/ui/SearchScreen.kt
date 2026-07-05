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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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

        TvLazyVerticalGrid(
            columns = TvGridCells.Fixed(5),
            state = rememberTvLazyGridState(),
            modifier = Modifier.fillMaxSize(),
            // Reserve room so the focused card (scaled up to 1.1×) is never clipped at the edges.
            contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
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
    Surface(
        onClick = onClick,
        // No fixed width: the grid cell drives the card width (~5 across on the 960dp canvas).
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(cardShape),
        // Keep the card DARK in both states (default + focused) so text stays readable. By
        // default a clickable Surface flips to a light focusedContainerColor, which produced
        // white text on a white background on focus. We signal focus via scale + border only,
        // and pin container/content colors dark for every state.
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colorScheme.surfaceVariant,
            contentColor = colorScheme.onSurface,
            focusedContainerColor = colorScheme.surfaceVariant,
            focusedContentColor = colorScheme.onSurface,
            pressedContainerColor = colorScheme.surfaceVariant,
            pressedContentColor = colorScheme.onSurface,
        ),
        // Obvious focus state required by TV UX: scale up + brand-colored border on focus.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, colorScheme.primary),
                shape = cardShape,
            ),
        ),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (result.posterUri != null) {
                    AsyncImage(
                        model = result.posterUri,
                        contentDescription = result.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Hand-off/deep-link cards carry no poster. SearchResult exposes no package
                    // name, so instead of a bare first letter we show a branded, centered source
                    // label on a theme-colored tile so the card reads as intentional.
                    PlaceholderTile(label = result.sourceLabel)
                }
            }

            Spacer(Modifier.height(8.dp))

            KindBadge(result.kind)

            Spacer(Modifier.height(4.dp))

            Text(
                text = result.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Text(
                text = result.sourceLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaceholderTile(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
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
