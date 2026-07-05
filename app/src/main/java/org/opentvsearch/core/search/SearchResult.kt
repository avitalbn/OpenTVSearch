package org.opentvsearch.core.search

import android.content.Intent
import android.net.Uri

/**
 * A single content hit surfaced by a [SearchSource].
 *
 * Two flavours exist, distinguished by [launch]:
 *  - INLINE result: we resolved a concrete item (title/poster/type) and have a
 *    deep-link [launch] intent that plays/opens it directly in the owning app.
 *    Produced by TV-Provider searchable rows and open-app API adapters.
 *  - HANDOFF result: we could NOT read the app's catalog, so [launch] opens the
 *    target app pre-filled with the user's query (ACTION_SEARCH / URL scheme).
 */
data class SearchResult(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val posterUri: Uri? = null,
    val type: ContentType = ContentType.UNKNOWN,
    val sourceId: String,
    val sourceLabel: String,
    val kind: ResultKind,
    /** Intent that launches this item (inline) or the app's search (handoff). */
    val launch: Intent,
)

enum class ContentType { MOVIE, TV_SERIES, TV_EPISODE, CLIP, CHANNEL, TRACK, UNKNOWN }

enum class ResultKind {
    /** Concrete content item resolved from a readable source. */
    INLINE,
    /** Opens the target app's search pre-filled with the query. */
    HANDOFF,
}
