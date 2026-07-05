package org.opentvsearch.core.apps

import org.opentvsearch.sources.handoff.HandoffStrategy

/**
 * PURE (Android-free) catalog for the Discover home's content-app rail, mirroring the style of
 * [org.opentvsearch.core.sources.SourceCatalog]: a deterministic mapping of inputs to an ordered
 * list, unit-testable without Robolectric.
 *
 * Drawable/intent resolution (banner, icon, launch intent) is NOT here — that touches
 * [android.content.pm.PackageManager] and lives in the Android layer
 * ([ContentAppRepository]). This object only decides WHICH apps show and in WHAT order.
 */
object ContentAppCatalog {

    /**
     * Ordered content apps to show on the Discover home.
     *
     * Candidates = the [recommended] registry entries that are INSTALLED (present in
     * [installedPackages]). Order = the RECOMMENDED registry order (recommended apps pinned to the
     * top; the registry itself is the curated priority list). Not-installed recommended apps are
     * omitted — the Discover rail only shows apps the user actually has.
     *
     * `searchable` is derived from each app's [HandoffStrategy]: any strategy that carries a query
     * (Nova's ACTION_SEARCH_COMPONENT, SmartTube's URL_TEMPLATE, ACTION_SEARCH, GMS_SEARCH_ACTION)
     * is searchable; only [HandoffStrategy.LAUNCH_ONLY] is not.
     *
     * @param recommended the RECOMMENDED registry (package + label + hand-off strategy).
     * @param installedPackages leanback packages currently installed on the device.
     */
    fun buildContentApps(
        recommended: List<RecommendedApp>,
        installedPackages: Set<String>,
    ): List<InstalledContentApp> =
        recommended
            .filter { it.packageName in installedPackages }
            .map { app ->
                InstalledContentApp(
                    packageName = app.packageName,
                    label = app.label,
                    recommended = true,
                    searchable = app.handoffStrategy != HandoffStrategy.LAUNCH_ONLY,
                )
            }
}

/**
 * A content app to show on the Discover rail. PURE metadata only — visuals (banner/icon) and the
 * launch/search intent are resolved in the Android layer from this app's [packageName].
 *
 * @property searchable true when the app honors an external query (Nova/SmartTube); false for
 *   launch-only apps (Stremio/Netflix/Kan/YouTube). Drives the "Searchable" vs "Opens app" chip and
 *   whether a tile click attempts a real search or just opens the app.
 */
data class InstalledContentApp(
    val packageName: String,
    val label: String,
    val recommended: Boolean,
    val searchable: Boolean,
)
