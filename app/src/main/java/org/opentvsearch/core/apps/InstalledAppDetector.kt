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
         * Curated registry of recommended content apps and how to hand off a search
         * to each when its catalog can't be read. Verified strategies should be
         * confirmed against installed APKs before shipping (see README).
         */
        val RECOMMENDED: List<RecommendedApp> = listOf(
            RecommendedApp("com.stremio.one", "Stremio", HandoffStrategy.URL_TEMPLATE,
                "stremio://search?query={q}"),
            RecommendedApp("com.google.android.youtube.tv", "YouTube", HandoffStrategy.URL_TEMPLATE,
                "https://www.youtube.com/results?search_query={q}"),
            RecommendedApp("org.courville.nova", "Nova Player", HandoffStrategy.ACTION_SEARCH),
            RecommendedApp("org.jellyfin.androidtv", "Jellyfin", HandoffStrategy.ACTION_SEARCH),
            RecommendedApp("org.xbmc.kodi", "Kodi", HandoffStrategy.GMS_SEARCH_ACTION),
            RecommendedApp("com.netflix.ninja", "Netflix", HandoffStrategy.ACTION_SEARCH),
            RecommendedApp("com.plexapp.android", "Plex", HandoffStrategy.ACTION_SEARCH),
        )
    }
}

data class RecommendedApp(
    val packageName: String,
    val label: String,
    val handoffStrategy: HandoffStrategy,
    val urlTemplate: String? = null,
)
