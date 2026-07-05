package org.opentvsearch.sources.handoff

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/**
 * The SINGLE place that turns a [HandoffTarget] + query into a hand-off [Intent]. Both
 * [DeepLinkHandoffSource] (result cards) and the Discover home's tile-launch path call
 * [create], so the DEVICE-VERIFIED per-app strategies (Nova component search, SmartTube
 * `vnd.youtube` URL, launch-only fallbacks) stay defined here and only here.
 *
 * Two layers, mirroring the codebase's parse-don't-validate style (see
 * [org.opentvsearch.sources.tvprovider.TvProviderSearchSource.mapRow], which injects the intent
 * parser to stay pure/testable):
 *   - [spec]  — PURE. Decides WHICH intent shape + values a target+query produces, as a plain
 *     [HandoffIntentSpec] with no live Android Intent. Unit-testable without Robolectric (the
 *     stub android.jar cannot round-trip Intent action/component/extras).
 *   - [create] — mechanically materializes that spec into a real [Intent]. No strategy decisions
 *     live here; it is a 1:1 spec→Intent mapping.
 *
 * The behavior is byte-for-byte the same as the old private `DeepLinkHandoffSource.buildIntent`.
 */
object HandoffIntentFactory {

    /**
     * PURE description of the hand-off intent for [target] + [query], or null when the target is
     * missing required data (e.g. an ACTION_SEARCH_COMPONENT target with no search activity, or a
     * URL_TEMPLATE target with no template).
     *
     * @param encode how to percent-encode the query into a URL_TEMPLATE. Defaults to
     *   [Uri.encode]; overridable in tests so the produced URL can be asserted without the Android
     *   runtime (the stub android.jar returns null from `Uri.encode`).
     */
    fun spec(
        target: HandoffTarget,
        query: String,
        encode: (String) -> String = { Uri.encode(it) },
    ): HandoffIntentSpec? = when (target.strategy) {
        HandoffStrategy.URL_TEMPLATE ->
            target.urlTemplate?.let { template ->
                HandoffIntentSpec.ViewUrl(target.packageName, template.replace("{q}", encode(query)))
            }

        // Targets a SPECIFIC search activity (component), not just the package, because a bare
        // ACTION_SEARCH + setPackage resolves to an arbitrary/main activity and the query is
        // dropped. The component must be an activity that consumes SearchManager.QUERY.
        HandoffStrategy.ACTION_SEARCH_COMPONENT ->
            target.searchActivity?.let { activity ->
                HandoffIntentSpec.ComponentSearch(target.packageName, activity, query)
            }

        // Legacy package-scoped ACTION_SEARCH. Prefer ACTION_SEARCH_COMPONENT; verify on device.
        HandoffStrategy.ACTION_SEARCH ->
            HandoffIntentSpec.ActionSearchPackage(target.packageName, query)

        HandoffStrategy.GMS_SEARCH_ACTION ->
            HandoffIntentSpec.GmsSearch(target.packageName, query)

        // No working external-query path: just open the app so the user can search inside it.
        HandoffStrategy.LAUNCH_ONLY ->
            HandoffIntentSpec.Launch(target.packageName)
    }

    /** Materializes [spec] into a real [Intent] (see [HandoffIntentSpec]). */
    fun create(target: HandoffTarget, query: String): Intent? =
        when (val s = spec(target, query)) {
            is HandoffIntentSpec.ViewUrl ->
                Intent(Intent.ACTION_VIEW, s.url.toUri()).setPackage(s.packageName)

            is HandoffIntentSpec.ComponentSearch ->
                Intent(Intent.ACTION_SEARCH).apply {
                    setClassName(s.packageName, s.activity)
                    putExtra("query", s.query) // android.app.SearchManager.QUERY
                }

            is HandoffIntentSpec.ActionSearchPackage ->
                Intent(Intent.ACTION_SEARCH)
                    .setPackage(s.packageName)
                    .putExtra("query", s.query)

            is HandoffIntentSpec.GmsSearch ->
                Intent("com.google.android.gms.actions.SEARCH_ACTION")
                    .setPackage(s.packageName)
                    .putExtra("query", s.query)

            is HandoffIntentSpec.Launch ->
                target.launchIntent()

            null -> null
        }
}

/**
 * A framework-free description of the hand-off intent to build. Exists so the per-app strategy
 * decisions can be unit-tested (a real [Intent] cannot be inspected under the stub android.jar).
 */
sealed interface HandoffIntentSpec {
    val packageName: String

    /** ACTION_VIEW of [url], package-pinned (SmartTube `vnd.youtube://results?search_query=…`). */
    data class ViewUrl(override val packageName: String, val url: String) : HandoffIntentSpec

    /** ACTION_SEARCH targeting a specific [activity] with [query] (Nova). */
    data class ComponentSearch(
        override val packageName: String,
        val activity: String,
        val query: String,
    ) : HandoffIntentSpec

    /** Package-scoped ACTION_SEARCH carrying [query]. */
    data class ActionSearchPackage(override val packageName: String, val query: String) :
        HandoffIntentSpec

    /** GMS SEARCH_ACTION carrying [query]. */
    data class GmsSearch(override val packageName: String, val query: String) : HandoffIntentSpec

    /** No external query path: open the app to its normal entry point. */
    data class Launch(override val packageName: String) : HandoffIntentSpec
}
