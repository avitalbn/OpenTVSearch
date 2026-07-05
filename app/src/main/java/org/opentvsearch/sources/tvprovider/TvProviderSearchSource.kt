package org.opentvsearch.sources.tvprovider

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.tvprovider.media.tv.BasePreviewProgram
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.opentvsearch.core.search.ContentType
import org.opentvsearch.core.search.ResultKind
import org.opentvsearch.core.search.SearchResult
import org.opentvsearch.core.search.SearchSource
import org.opentvsearch.core.search.SourceCapability
import kotlin.coroutines.coroutineContext

/**
 * Reads content that OTHER installed apps published to the system TV Provider and
 * marked COLUMN_SEARCHABLE=1, via the READ_TV_LISTINGS runtime permission.
 *
 * Tables: preview_programs, watch_next_programs (API 26+).
 * Useful columns: COLUMN_TITLE, COLUMN_POSTER_ART_URI, COLUMN_INTENT_URI
 * (deep-link to play the item), COLUMN_TYPE, COLUMN_SEARCHABLE.
 *
 * This is the ONLY supported cross-app content READ on Android/Google TV.
 *
 * Behaviour contract (see IMPLEMENTATION_BRIEF_M1.md):
 *  - Below API 26 → [emptyList].
 *  - READ_TV_LISTINGS not granted → [emptyList] (the UI layer requests it; this
 *    source must never crash).
 *  - Never throws for expected failures (missing provider, SecurityException):
 *    everything is wrapped and degrades to [emptyList].
 *  - Runs off the main thread on [Dispatchers.IO] and is cancellation-cooperative.
 */
class TvProviderSearchSource(
    private val context: Context,
) : SearchSource {

    override val id = "tvprovider"
    override val label = "Installed apps (Watch Next / recommendations)"
    override val capability = SourceCapability.INLINE_RESULTS

    override suspend fun search(query: String): List<SearchResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        // Feature gate: the preview/watch-next tables + READ_TV_LISTINGS require API 26 (O)+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()

        // Permission gate: never crash if the runtime permission is not granted.
        if (ContextCompat.checkSelfPermission(context, PERMISSION_READ_TV_LISTINGS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                queryTable(TvContractCompat.PreviewPrograms.CONTENT_URI, q, preview = true) +
                    queryTable(TvContractCompat.WatchNextPrograms.CONTENT_URI, q, preview = false)
            }.getOrDefault(emptyList())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun queryTable(uri: Uri, query: String, preview: Boolean): List<SearchResult> {
        val out = ArrayList<SearchResult>()
        // projection=null → all columns, so PreviewProgram.fromCursor can populate fully
        // and we can also read package_name directly. selection is left null: there is no
        // portable server-side LIKE contract across OEMs, so we filter titles in memory.
        val cursor: Cursor = context.contentResolver.query(uri, null, null, null, null)
            ?: return emptyList()
        cursor.use { c ->
            val pkgIndex = c.getColumnIndex(COLUMN_PACKAGE_NAME)
            while (c.moveToNext()) {
                coroutineContext.ensureActive() // cancellation-cooperative
                val row = runCatching {
                    val program: BasePreviewProgram =
                        if (preview) PreviewProgram.fromCursor(c) else WatchNextProgram.fromCursor(c)
                    val pkg = if (pkgIndex >= 0) c.getString(pkgIndex) else null
                    extractRow(program, pkg)
                }.getOrNull() ?: continue
                mapRow(row, query, id, label)?.let(out::add)
            }
        }
        return out
    }

    companion object {
        /**
         * Runtime permission literal. We use the string (which matches the manifest) rather than
         * `android.Manifest.permission.READ_TV_LISTINGS`, whose Java constant is not exposed on
         * this compile SDK's stub android.jar.
         */
        const val PERMISSION_READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

        /** AOSP column name shared by all TV-Provider tables (BaseTvColumns.package_name). */
        @VisibleForTesting
        internal const val COLUMN_PACKAGE_NAME = "package_name"

        /** Extracts the raw, provider-agnostic fields we care about from a program row. */
        @VisibleForTesting
        internal fun extractRow(program: BasePreviewProgram, packageName: String?): RawRow = RawRow(
            packageName = packageName,
            internalProviderId = program.internalProviderId,
            rowId = program.id,
            title = program.title,
            posterArtUri = program.posterArtUri?.toString(),
            intentUri = program.intentUri?.toString(),
            type = program.type,
        )

        /**
         * Pure cursor-row → [SearchResult] mapping. Kept free of Android framework I/O so it
         * can be unit-tested without Robolectric. Returns null when the row must be skipped:
         *  - blank title,
         *  - title does not contain [query] case-insensitively,
         *  - blank or unparseable COLUMN_INTENT_URI (a result with no launch target is useless).
         *
         * [parseIntent] is injected so tests can supply launch intents without the Android
         * runtime; production passes [Intent.parseUri].
         */
        @VisibleForTesting
        internal fun mapRow(
            row: RawRow,
            query: String,
            sourceId: String,
            sourceLabel: String,
            parseIntent: (String) -> Intent? = ::parseLaunchIntent,
        ): SearchResult? {
            val title = row.title?.trim().orEmpty()
            if (title.isEmpty()) return null
            if (!title.contains(query.trim(), ignoreCase = true)) return null

            val intentUri = row.intentUri?.trim().orEmpty()
            if (intentUri.isEmpty()) return null
            val launch = runCatching { parseIntent(intentUri) }.getOrNull() ?: return null

            val pkg = row.packageName?.takeIf { it.isNotBlank() } ?: "unknown"
            val stableKey = row.internalProviderId?.takeIf { it.isNotBlank() }
                ?: row.rowId.toString()

            return SearchResult(
                id = "$pkg:$stableKey",
                title = title,
                subtitle = sourceLabel,
                posterUri = row.posterArtUri
                    ?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { Uri.parse(it) }.getOrNull() },
                type = mapType(row.type),
                sourceId = sourceId,
                sourceLabel = sourceLabel,
                kind = ResultKind.INLINE,
                packageName = row.packageName?.takeIf { it.isNotBlank() },
                launch = launch,
            )
        }

        private fun parseLaunchIntent(intentUri: String): Intent? =
            runCatching { Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME) }.getOrNull()

        /** Maps the TV-Provider COLUMN_TYPE int to our [ContentType]. */
        @VisibleForTesting
        internal fun mapType(type: Int?): ContentType = when (type) {
            TvContractCompat.PreviewPrograms.TYPE_MOVIE -> ContentType.MOVIE
            TvContractCompat.PreviewPrograms.TYPE_TV_SERIES -> ContentType.TV_SERIES
            TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE -> ContentType.TV_EPISODE
            TvContractCompat.PreviewPrograms.TYPE_CLIP -> ContentType.CLIP
            TvContractCompat.PreviewPrograms.TYPE_CHANNEL -> ContentType.CHANNEL
            TvContractCompat.PreviewPrograms.TYPE_TRACK -> ContentType.TRACK
            else -> ContentType.UNKNOWN
        }
    }
}

/** Provider-agnostic snapshot of the row fields used to build a [SearchResult]. */
@VisibleForTesting
internal data class RawRow(
    val packageName: String?,
    val internalProviderId: String?,
    val rowId: Long,
    val title: String?,
    val posterArtUri: String?,
    val intentUri: String?,
    val type: Int?,
)
