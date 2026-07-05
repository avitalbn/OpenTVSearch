package org.opentvsearch.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
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
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { androidx.compose.material3.Text("Search movies, shows, clips") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onSubmit() },
                ),
            )
            Button(onClick = onSubmit) { Text("Search") }
            Button(onClick = onVoice) { Text("\uD83C\uDFA4 Voice") }
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
            columns = TvGridCells.Fixed(4),
            state = rememberTvLazyGridState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ResultCard(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(180.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center,
            ) {
                if (result.posterUri != null) {
                    AsyncImage(
                        model = result.posterUri,
                        contentDescription = result.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(result.title.take(1).uppercase())
                }
            }

            Spacer(Modifier.height(8.dp))

            KindBadge(result.kind)

            Spacer(Modifier.height(4.dp))

            Text(
                text = result.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
            )
            Text(
                text = result.sourceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
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
