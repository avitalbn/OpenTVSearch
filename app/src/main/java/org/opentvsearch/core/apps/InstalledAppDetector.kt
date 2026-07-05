package org.opentvsearch.core.apps

import android.content.Context
import android.content.Intent
import org.opentvsearch.sources.handoff.HandoffStrategy

/**
 * Enumerates installed TV apps (CATEGORY_LEANBACK_LAUNCHER) and cross-references a
 * curated recommended-apps registry so known content apps (Stremio, Netflix, Nova,
 * Jellyfin, Kodi, YouTube, Plex, ...) get PINNED to the top of the source list.
 *
 * TODO(v1): implement queryIntentActivities(LEANBACK_LAUNCHER) + map to
 *   SourceDescriptor, marking recommended=true for packages in RECOMMENDED.
 */
class InstalledAppDetector(
    private val context: Context,
) {
    fun installedLeanbackPackages(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return context.packageManager
            .queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
    }

    companion object {
        /**
         * Curated registry of recommended content apps and how to hand off a search to each.
         *
         * STRATEGIES ARE DEVICE-TESTED, NOT ASSUMED. Verified on a Skyworth HP4609 (Android 14):
         *   - Nova: real search works ONLY via the explicit QueryBrowserActivityVideo component
         *     (a bare package-scoped ACTION_SEARCH drops the query) -> ACTION_SEARCH_COMPONENT.
         *   - Stremio: the `stremio://search?query=` scheme resolves but the app IGNORES the query
         *     and opens to home; no external search path exists -> LAUNCH_ONLY.
         *   - Netflix (ninja): declares NO `/search` deep-link path (only /title, /watch, /browse,
         *     /home, /deeplink); external search is impossible -> LAUNCH_ONLY.
         *   - YouTube TV: exposes no ACTION_SEARCH / GMS SEARCH_ACTION, and the vnd.youtube search
         *     scheme is claimed by SmartTube; no reliable external search -> LAUNCH_ONLY.
         *   - Jellyfin/Kodi/Plex: UNVERIFIED on this device -> conservative LAUNCH_ONLY until a
         *     query-carrying strategy is confirmed on a real device (fire intent + screenshot).
         *
         * Before promoting any app to a query-carrying strategy, TEST it: `am start` the intent
         * on a device and confirm (screenshot) the query actually lands, not just that the app opens.
         */
        val RECOMMENDED: List<RecommendedApp> = listOf(
            // Nova: real in-app search via the specific query browser activity (VERIFIED).
            RecommendedApp(
                "org.courville.nova", "Nova Player",
                HandoffStrategy.ACTION_SEARCH_COMPONENT,
                searchActivity = "com.archos.mediacenter.video.browser.QueryBrowserActivityVideo",
            ),
            // Ignore external queries / no external search path -> honest launch-only (VERIFIED).
            RecommendedApp("com.stremio.one", "Stremio", HandoffStrategy.LAUNCH_ONLY),
            RecommendedApp("com.netflix.ninja", "Netflix", HandoffStrategy.LAUNCH_ONLY),
            RecommendedApp("com.google.android.youtube.tv", "YouTube", HandoffStrategy.LAUNCH_ONLY),
            // Unverified on this device -> conservative launch-only until tested.
            RecommendedApp("org.jellyfin.androidtv", "Jellyfin", HandoffStrategy.LAUNCH_ONLY),
            RecommendedApp("org.xbmc.kodi", "Kodi", HandoffStrategy.LAUNCH_ONLY),
            RecommendedApp("com.plexapp.android", "Plex", HandoffStrategy.LAUNCH_ONLY),
        )
    }
}

data class RecommendedApp(
    val packageName: String,
    val label: String,
    val handoffStrategy: HandoffStrategy,
    val urlTemplate: String? = null,
    val searchActivity: String? = null,
)
