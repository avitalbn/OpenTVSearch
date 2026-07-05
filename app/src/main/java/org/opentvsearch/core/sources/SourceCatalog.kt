package org.opentvsearch.core.sources

import org.opentvsearch.core.apps.RecommendedApp
import org.opentvsearch.core.search.SourceCapability
import org.opentvsearch.core.search.SourceDescriptor

/**
 * PURE (Android-free) source-catalog + ordering logic shared by the Settings UI and the live
 * source builder in AppModule. Kept free of framework I/O so it can be unit-tested without
 * Robolectric: every function is a deterministic mapping of its inputs.
 *
 * Source id convention (must match the LIVE source ids so ordering/enabling keys line up):
 *   - [TV_PROVIDER_ID] == TvProviderSearchSource.id == "tvprovider"
 *   - [handoffId] == DeepLinkHandoffSource.id == "handoff:<packageName>"
 */
object SourceCatalog {

    /** Live id of the TvProvider source (TvProviderSearchSource.id). */
    const val TV_PROVIDER_ID = "tvprovider"

    /** Live id of a hand-off source for [packageName] (DeepLinkHandoffSource.id). */
    fun handoffId(packageName: String): String = "handoff:$packageName"

    /**
     * Builds the settings catalog as an ORDERED list of [SourceDescriptor]s.
     *
     * Candidates:
     *   - one descriptor per RECOMMENDED app that is INSTALLED (recommended=true), and
     *   - the fixed TvProvider system source (recommended=false).
     *
     * Not-installed recommended apps are HIDDEN for M2 (prefer hidden over greyed to avoid
     * confusion — the brief's stated default).
     *
     * Default order (no saved order): recommended+installed apps first, in RECOMMENDED registry
     * order (pinned to top), then the TvProvider source. A saved [order] overrides this via
     * [applySourceOrder]: ids present in [order] come first in that order; ids absent keep their
     * natural position after the ordered ones.
     *
     * @param recommended the RECOMMENDED registry (label + package + capability source).
     * @param installedPackages leanback packages currently installed on the device.
     * @param disabledIds ids the user turned off (a descriptor is enabled iff its id is absent).
     * @param order the user's persisted preferred order (list of ids).
     */
    fun buildSourceCatalog(
        recommended: List<RecommendedApp>,
        installedPackages: Set<String>,
        disabledIds: Set<String>,
        order: List<String>,
    ): List<SourceDescriptor> {
        val recommendedDescriptors = recommended
            .filter { it.packageName in installedPackages }
            .map { app ->
                SourceDescriptor(
                    id = handoffId(app.packageName),
                    label = app.label,
                    packageName = app.packageName,
                    // Hand-off entries only ever OPEN an app (INLINE reads come from TvProvider).
                    capability = SourceCapability.HANDOFF_ONLY,
                    recommended = true,
                    installed = true,
                    enabled = handoffId(app.packageName) !in disabledIds,
                )
            }

        val tvProviderDescriptor = SourceDescriptor(
            id = TV_PROVIDER_ID,
            label = "Installed apps (Watch Next / recommendations)",
            packageName = null,
            capability = SourceCapability.INLINE_RESULTS,
            recommended = false,
            installed = true,
            enabled = TV_PROVIDER_ID !in disabledIds,
        )

        // Natural order: recommended (pinned) first, then the system TvProvider source.
        val natural = recommendedDescriptors + tvProviderDescriptor
        return applySourceOrder(natural, order) { it.id }
    }

    /**
     * Reorders [items] by the persisted [order]:
     *   - items whose id appears in [order] come first, in [order]'s sequence;
     *   - items whose id is NOT in [order] keep their natural relative position, after the
     *     ordered ones.
     * Ids in [order] with no matching item are ignored (e.g. an uninstalled app).
     */
    fun <T> applySourceOrder(items: List<T>, order: List<String>, idOf: (T) -> String): List<T> {
        if (order.isEmpty()) return items
        val orderIndex = order.withIndex().associate { (i, id) -> id to i }
        val ordered = items
            .filter { orderIndex.containsKey(idOf(it)) }
            .sortedBy { orderIndex.getValue(idOf(it)) }
        val rest = items.filterNot { orderIndex.containsKey(idOf(it)) }
        return ordered + rest
    }

    /**
     * Filters out [disabledIds] then applies [order]. This is the exact selection the live source
     * builder in AppModule performs (extracted here so it is unit-testable without Android).
     */
    fun <T> selectEnabledOrdered(
        items: List<T>,
        disabledIds: Set<String>,
        order: List<String>,
        idOf: (T) -> String,
    ): List<T> {
        val enabled = items.filterNot { idOf(it) in disabledIds }
        return applySourceOrder(enabled, order, idOf)
    }

    /**
     * Returns a NEW id list with [id] moved one slot toward the front ([up] = true) or back.
     * A no-op (returns [currentIds] unchanged) when the id is missing or already at the edge.
     * Used by the Settings move-up/move-down affordances, which then persist the result.
     */
    fun moveOrder(currentIds: List<String>, id: String, up: Boolean): List<String> {
        val index = currentIds.indexOf(id)
        if (index < 0) return currentIds
        val target = if (up) index - 1 else index + 1
        if (target < 0 || target > currentIds.lastIndex) return currentIds
        return currentIds.toMutableList().apply {
            val tmp = this[target]
            this[target] = this[index]
            this[index] = tmp
        }
    }
}
