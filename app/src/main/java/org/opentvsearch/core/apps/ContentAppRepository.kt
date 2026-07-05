package org.opentvsearch.core.apps

import android.content.Context
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android layer for the Discover rail: resolves the pure [ContentAppCatalog] output into
 * ready-to-render [ContentAppTile]s (banner/icon [Drawable]s) via [android.content.pm.PackageManager].
 *
 * Everything here touches the framework, so it stays out of the pure catalog and is exercised on
 * device rather than in JVM unit tests.
 */
class ContentAppRepository(
    private val context: Context,
    private val detector: InstalledAppDetector,
) {
    suspend fun loadContentApps(): List<ContentAppTile> = withContext(Dispatchers.IO) {
        val installed = runCatching { detector.installedLeanbackPackages() }
            .getOrDefault(emptyList())
            .toSet()
        val pm = context.packageManager
        ContentAppCatalog.buildContentApps(InstalledAppDetector.RECOMMENDED, installed)
            .map { app ->
                ContentAppTile(
                    packageName = app.packageName,
                    label = app.label,
                    searchable = app.searchable,
                    // getApplicationBanner is null for apps that declare no android:banner → the
                    // tile falls back to the icon; getApplicationIcon can throw if the package
                    // vanished between enumeration and resolution, hence runCatching.
                    banner = runCatching { pm.getApplicationBanner(app.packageName) }.getOrNull(),
                    icon = runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull(),
                )
            }
    }
}

/**
 * A Discover-rail tile with its visuals resolved. [banner] is the wide 16:9 TV banner (null when
 * the app declares none → render [icon] + [label] instead); [icon] is null only if the package
 * could not be resolved at all.
 */
data class ContentAppTile(
    val packageName: String,
    val label: String,
    val searchable: Boolean,
    val banner: Drawable?,
    val icon: Drawable?,
)
