package org.opentvsearch.sources.handoff

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import org.opentvsearch.core.search.ContentType
import org.opentvsearch.core.search.ResultKind
import org.opentvsearch.core.search.SearchResult
import org.opentvsearch.core.search.SearchSource
import org.opentvsearch.core.search.SourceCapability

/**
 * Fallback for CLOSED apps whose catalog cannot be read. Emits a single HANDOFF
 * result per installed target that, when selected, opens that app pre-filled with
 * the query. This is how the user still "searches" Netflix/YouTube/Nova/etc.
 *
 * Strategy per target (in priority order, resolved at build time against the
 * installed app / verified intent):
 *   1. App-specific URL scheme (e.g. youtube results URL, stremio://).
 *   2. ACTION_SEARCH with SearchManager.QUERY extra, targeted at the package.
 *   3. com.google.android.gms.actions.SEARCH_ACTION.
 */
class DeepLinkHandoffSource(
    private val target: HandoffTarget,
) : SearchSource {

    override val id = "handoff:${target.packageName}"
    override val label = target.label
    override val capability = SourceCapability.HANDOFF_ONLY

    override suspend fun search(query: String): List<SearchResult> {
        val intent = buildIntent(query) ?: return emptyList()
        return listOf(
            SearchResult(
                id = "search:$query",
                title = "Search \"$query\" in ${target.label}",
                type = ContentType.UNKNOWN,
                sourceId = id,
                sourceLabel = target.label,
                kind = ResultKind.HANDOFF,
                launch = intent,
            )
        )
    }

    private fun buildIntent(query: String): Intent? = when (target.strategy) {
        HandoffStrategy.URL_TEMPLATE ->
            target.urlTemplate?.let {
                Intent(Intent.ACTION_VIEW, it.replace("{q}", Uri.encode(query)).toUri())
                    .setPackage(target.packageName)
            }
        HandoffStrategy.ACTION_SEARCH ->
            Intent(Intent.ACTION_SEARCH)
                .setPackage(target.packageName)
                .putExtra("query", query) // SearchManager.QUERY
        HandoffStrategy.GMS_SEARCH_ACTION ->
            Intent("com.google.android.gms.actions.SEARCH_ACTION")
                .setPackage(target.packageName)
                .putExtra("query", query)
    }
}

data class HandoffTarget(
    val packageName: String,
    val label: String,
    val strategy: HandoffStrategy,
    val urlTemplate: String? = null, // uses {q} placeholder
)

enum class HandoffStrategy { URL_TEMPLATE, ACTION_SEARCH, GMS_SEARCH_ACTION }
