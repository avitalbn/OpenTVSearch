package org.opentvsearch.sources.handoff

import android.content.Intent
import org.opentvsearch.core.search.ContentType
import org.opentvsearch.core.search.ResultKind
import org.opentvsearch.core.search.SearchResult
import org.opentvsearch.core.search.SearchSource
import org.opentvsearch.core.search.SourceCapability

/**
 * Fallback for CLOSED apps whose catalog cannot be read. Emits a single HANDOFF
 * result per installed target that, when selected, either:
 *   - runs a real in-app search (for apps that HONOR an external query), or
 *   - just LAUNCHES the app to its own search UI (for apps that ignore external
 *     queries — the honest fallback).
 *
 * IMPORTANT — strategies here are DEVICE-TESTED, not assumed. An app advertising
 * `ACTION_SEARCH` or a `://search` scheme in its manifest does NOT mean it acts on
 * an externally-supplied query; most community/streaming TV apps silently open to
 * home. Only ship a query-carrying strategy after confirming on a device that the
 * query actually lands (fire the intent + screenshot). See the project README and
 * the android-tv-adb-debloat skill's voice-search-into-tv-apps reference.
 *
 * Verified on a Skyworth HP4609 (Android 14):
 *   - Nova  -> ACTION_SEARCH_COMPONENT to QueryBrowserActivityVideo WORKS (real results).
 *   - Stremio, Netflix(ninja), YouTube TV -> LAUNCH_ONLY (no working external search path).
 */
class DeepLinkHandoffSource(
    private val target: HandoffTarget,
) : SearchSource {

    override val id = "handoff:${target.packageName}"
    override val label = target.label
    override val capability = SourceCapability.HANDOFF_ONLY

    override suspend fun search(query: String): List<SearchResult> {
        // Intent building lives in the shared HandoffIntentFactory so the Discover home reuses the
        // exact same device-verified per-app strategies (do NOT inline it back here).
        val intent = HandoffIntentFactory.create(target, query) ?: return emptyList()
        // Honest labeling: query-carrying strategies say "Search X"; launch-only says
        // "Open X to search" so the user knows they'll finish the search inside the app.
        val carriesQuery = target.strategy != HandoffStrategy.LAUNCH_ONLY
        val title = if (carriesQuery) {
            "Search \"$query\" in ${target.label}"
        } else {
            "Open ${target.label} to search"
        }
        return listOf(
            SearchResult(
                id = if (carriesQuery) "search:$query" else "launch",
                title = title,
                type = ContentType.UNKNOWN,
                sourceId = id,
                sourceLabel = target.label,
                kind = ResultKind.HANDOFF,
                packageName = target.packageName,
                launch = intent,
            )
        )
    }
}

data class HandoffTarget(
    val packageName: String,
    val label: String,
    val strategy: HandoffStrategy,
    val urlTemplate: String? = null,       // uses {q} placeholder (URL_TEMPLATE)
    val searchActivity: String? = null,    // fully-qualified activity (ACTION_SEARCH_COMPONENT)
) {
    /**
     * A plain launcher/main intent for LAUNCH_ONLY targets. Uses ACTION_MAIN so the app opens to
     * its normal entry point; the caller adds FLAG_ACTIVITY_NEW_TASK when starting from a
     * non-activity context if needed.
     */
    fun launchIntent(): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
}

enum class HandoffStrategy {
    URL_TEMPLATE,
    ACTION_SEARCH_COMPONENT,
    ACTION_SEARCH,
    GMS_SEARCH_ACTION,
    LAUNCH_ONLY,
}
