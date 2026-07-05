package org.opentvsearch.sources.jellyfin

import org.opentvsearch.core.search.SearchResult
import org.opentvsearch.core.search.SearchSource
import org.opentvsearch.core.search.SourceCapability

/**
 * Example OPEN-app adapter: a user-configured Jellyfin server returns real inline
 * results via its REST API (GET /Search/Hints?searchTerm=...&api_key=...).
 *
 * TODO(v1): Retrofit service + map Jellyfin SearchHint -> SearchResult, building a
 *   launch Intent that opens org.jellyfin.androidtv at the item (or plays it).
 *   Reference HTTP-client patterns in ARVIO's HomeServerRepository.
 */
class JellyfinSearchSource(
    private val config: JellyfinConfig,
) : SearchSource {

    override val id = "jellyfin:${config.serverId}"
    override val label = "Jellyfin (${config.serverName})"
    override val capability = SourceCapability.INLINE_RESULTS

    override suspend fun search(query: String): List<SearchResult> {
        // TODO(v1): call /Search/Hints and map results.
        return emptyList()
    }
}

data class JellyfinConfig(
    val serverId: String,
    val serverName: String,
    val baseUrl: String,
    val apiKey: String,
)
