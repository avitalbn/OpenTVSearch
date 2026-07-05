package org.opentvsearch.sources.tvprovider

import android.content.Intent
import androidx.tvprovider.media.tv.TvContractCompat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.opentvsearch.core.search.ContentType
import org.opentvsearch.core.search.ResultKind

/**
 * Pure unit tests for the cursor-row -> [org.opentvsearch.core.search.SearchResult] mapping.
 *
 * Per the M1 brief we test the pure [TvProviderSearchSource.mapRow] / [TvProviderSearchSource.mapType]
 * functions directly rather than driving a real ContentResolver through Robolectric. The launch
 * Intent is supplied through the injected [parseIntent] lambda so no Android runtime is needed.
 *
 * The two runtime gates in [TvProviderSearchSource.search] — SDK < O returns empty, and
 * READ_TV_LISTINGS-not-granted returns empty — are early `return emptyList()` guards that require
 * an Android Context/permission environment; they are verified on-device / via instrumented tests
 * (see the QA checklist), keeping this suite pure and dependency-light as the brief prefers.
 */
class TvProviderSearchSourceTest {

    // Injected parser: non-blank uri -> a (stub) Intent, blank/whitespace -> null (skip).
    private val fakeParser: (String) -> Intent? = { uri ->
        if (uri.isBlank()) null else Intent()
    }

    private fun row(
        title: String? = "The Batman",
        intentUri: String? = "https://example.com/play/1",
        type: Int? = TvContractCompat.PreviewPrograms.TYPE_MOVIE,
        packageName: String? = "com.example.app",
        internalProviderId: String? = "abc123",
        rowId: Long = 42L,
        posterArtUri: String? = "https://example.com/poster.jpg",
    ) = RawRow(
        packageName = packageName,
        internalProviderId = internalProviderId,
        rowId = rowId,
        title = title,
        posterArtUri = posterArtUri,
        intentUri = intentUri,
        type = type,
    )

    private fun map(r: RawRow, query: String = "batman") =
        TvProviderSearchSource.mapRow(r, query, "tvprovider", "Installed apps", fakeParser)

    @Test
    fun `valid row maps to an INLINE result with title, type and non-null launch intent`() {
        val result = map(row())
        assertThat(result).isNotNull()
        result!!
        assertThat(result.kind).isEqualTo(ResultKind.INLINE)
        assertThat(result.title).isEqualTo("The Batman")
        assertThat(result.type).isEqualTo(ContentType.MOVIE)
        assertThat(result.launch).isNotNull()
        assertThat(result.sourceId).isEqualTo("tvprovider")
    }

    @Test
    fun `row with blank intent uri is skipped`() {
        assertThat(map(row(intentUri = ""))).isNull()
        assertThat(map(row(intentUri = "   "))).isNull()
        assertThat(map(row(intentUri = null))).isNull()
    }

    @Test
    fun `row with blank title is skipped`() {
        assertThat(map(row(title = ""))).isNull()
        assertThat(map(row(title = null))).isNull()
    }

    @Test
    fun `title filter is a case-insensitive contains match`() {
        assertThat(map(row(title = "The Batman"), query = "batman")).isNotNull()
        assertThat(map(row(title = "The Batman"), query = "BAT")).isNotNull()
        assertThat(map(row(title = "The Batman"), query = "superman")).isNull()
    }

    @Test
    fun `id is stable and unique - packageName plus internalProviderId, falling back to rowId`() {
        assertThat(map(row(packageName = "com.x", internalProviderId = "pid"))!!.id)
            .isEqualTo("com.x:pid")
        assertThat(map(row(packageName = "com.x", internalProviderId = null, rowId = 7L))!!.id)
            .isEqualTo("com.x:7")
        assertThat(map(row(packageName = null, internalProviderId = null, rowId = 7L))!!.id)
            .isEqualTo("unknown:7")
    }

    @Test
    fun `type column maps to ContentType`() {
        assertThat(TvProviderSearchSource.mapType(TvContractCompat.PreviewPrograms.TYPE_MOVIE))
            .isEqualTo(ContentType.MOVIE)
        assertThat(TvProviderSearchSource.mapType(TvContractCompat.PreviewPrograms.TYPE_TV_SERIES))
            .isEqualTo(ContentType.TV_SERIES)
        assertThat(TvProviderSearchSource.mapType(TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE))
            .isEqualTo(ContentType.TV_EPISODE)
        assertThat(TvProviderSearchSource.mapType(TvContractCompat.PreviewPrograms.TYPE_CLIP))
            .isEqualTo(ContentType.CLIP)
        assertThat(TvProviderSearchSource.mapType(TvContractCompat.PreviewPrograms.TYPE_CHANNEL))
            .isEqualTo(ContentType.CHANNEL)
        assertThat(TvProviderSearchSource.mapType(TvContractCompat.PreviewPrograms.TYPE_TRACK))
            .isEqualTo(ContentType.TRACK)
        assertThat(TvProviderSearchSource.mapType(null)).isEqualTo(ContentType.UNKNOWN)
        assertThat(TvProviderSearchSource.mapType(-999)).isEqualTo(ContentType.UNKNOWN)
    }
}
