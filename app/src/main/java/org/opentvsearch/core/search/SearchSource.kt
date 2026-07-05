package org.opentvsearch.core.search

import kotlinx.coroutines.flow.Flow

/**
 * The extension seam OpenTVSearch is built around. Every place content can come
 * from — TV-Provider searchable rows, a Jellyfin server, a Stremio addon, a
 * deep-link hand-off into a closed app — implements this ONE interface, and the
 * [SearchAggregator] fans a query out to all enabled sources in parallel.
 *
 * (This is precisely the abstraction ARVIO lacked: its "search" was a single
 *  TMDB call with no source seam.)
 */
interface SearchSource {

    /** Stable id used for config/ordering (e.g. "tvprovider", "jellyfin:home"). */
    val id: String

    /** Human label shown in the source list and on result cards. */
    val label: String

    /** Whether inline results or only deep-link hand-off is possible here. */
    val capability: SourceCapability

    /**
     * Query this source. Should be cancellation-cooperative and must never throw
     * for expected failures (return empty). Runs off the main thread.
     */
    suspend fun search(query: String): List<SearchResult>
}

enum class SourceCapability {
    /** Returns real content items (TV-Provider rows, open-app APIs). */
    INLINE_RESULTS,
    /** Only opens the app pre-filled with the query. */
    HANDOFF_ONLY,
}

/**
 * User-facing description of a candidate source for the configurable/pinned list.
 * Distinct from a live [SearchSource]: this is the *catalog entry* shown in
 * settings before the user enables/orders it.
 */
data class SourceDescriptor(
    val id: String,
    val label: String,
    val packageName: String?,        // installed app this maps to, if any
    val capability: SourceCapability,
    val recommended: Boolean,        // pinned to top when true (Stremio/Netflix/Nova/...)
    val installed: Boolean,
    val enabled: Boolean,
)
