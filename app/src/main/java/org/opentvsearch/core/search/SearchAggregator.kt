package org.opentvsearch.core.search

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fans a query out to every enabled [SearchSource] in parallel, applies a per-source
 * timeout so one slow server can't stall results, and merges hits with INLINE results
 * ranked ahead of HANDOFF, then by source order (pinned/recommended first).
 */
class SearchAggregator(
    private val sourcesProvider: suspend () -> List<SearchSource>,
    private val perSourceTimeoutMs: Long = 4_000,
) {
    suspend fun search(query: String): List<SearchResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val sources = sourcesProvider()

        val merged = coroutineScope {
            sources.mapIndexed { index, source ->
                async {
                    val hits = withTimeoutOrNull(perSourceTimeoutMs) {
                        runCatching { source.search(q) }.getOrDefault(emptyList())
                    } ?: emptyList()
                    hits.map { index to it }
                }
            }.awaitAll().flatten()
        }

        return merged
            .sortedWith(
                compareBy<Pair<Int, SearchResult>>(
                    { if (it.second.kind == ResultKind.INLINE) 0 else 1 }, // inline first
                    { it.first },                                          // then source order
                )
            )
            .map { it.second }
            .distinctBy { "${it.sourceId}:${it.id}" }
    }
}
