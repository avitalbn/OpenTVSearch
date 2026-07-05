package org.opentvsearch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import org.opentvsearch.core.apps.ContentAppTile

/**
 * Discover home rail shown when OpenTVSearch is idle (no query, no results): a "Your content apps"
 * header over a [TvLazyRow] of banner tiles for the installed content apps (recommended pinned
 * first). Clicking a tile hands off via [onLaunchApp] — a real search when the app is searchable
 * and there is a query, otherwise it just opens the app.
 *
 * @param onLaunchApp called with (packageName, withQuery); withQuery = the tile's `searchable`
 *   flag, i.e. whether the caller should attempt a query-carrying search intent.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DiscoverRow(
    apps: List<ContentAppTile>,
    onLaunchApp: (packageName: String, withQuery: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) return

    val firstTileFocus = remember { FocusRequester() }
    LaunchedEffect(apps) {
        runCatching { firstTileFocus.requestFocus() }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Your content apps",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        TvLazyRow(
            state = rememberTvLazyListState(),
            // Leave room so the focused tile (scaled to 1.1×) is never clipped at the row edges.
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(
                items = apps,
                key = { _, app -> app.packageName },
            ) { index, app ->
                ContentAppBannerTile(
                    tile = app,
                    onClick = { onLaunchApp(app.packageName, app.searchable) },
                    modifier = if (index == 0) Modifier.focusRequester(firstTileFocus) else Modifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContentAppBannerTile(
    tile: ContentAppTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tileShape = RoundedCornerShape(12.dp)
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.width(240.dp),
        shape = ClickableSurfaceDefaults.shape(tileShape),
        // Pin container/content colors DARK in every state (default/focused/pressed) so text and
        // fallback tiles never become white-on-white on focus; focus is signaled via scale+border.
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colorScheme.surfaceVariant,
            contentColor = colorScheme.onSurface,
            focusedContainerColor = colorScheme.surfaceVariant,
            focusedContentColor = colorScheme.onSurface,
            pressedContainerColor = colorScheme.surfaceVariant,
            pressedContentColor = colorScheme.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, colorScheme.primary),
                shape = tileShape,
            ),
        ),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    tile.banner != null -> AsyncImage(
                        model = tile.banner,
                        contentDescription = tile.label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )

                    tile.icon != null -> AsyncImage(
                        model = tile.icon,
                        contentDescription = tile.label,
                        modifier = Modifier.height(56.dp).aspectRatio(1f),
                    )

                    else -> Text(
                        text = tile.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = tile.label,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(4.dp))

            CapabilityChip(searchable = tile.searchable)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CapabilityChip(searchable: Boolean) {
    val label = if (searchable) "\uD83D\uDD0D Searchable" else "Opens app"
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
