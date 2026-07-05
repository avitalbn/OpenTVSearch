package org.opentvsearch.core.sources

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.opentvsearch.core.apps.RecommendedApp
import org.opentvsearch.core.search.SourceCapability
import org.opentvsearch.sources.handoff.HandoffStrategy

class SourceCatalogTest {

    private val recommended = listOf(
        RecommendedApp("org.courville.nova", "Nova Player", HandoffStrategy.ACTION_SEARCH_COMPONENT),
        RecommendedApp("com.stremio.one", "Stremio", HandoffStrategy.LAUNCH_ONLY),
        RecommendedApp("com.netflix.ninja", "Netflix", HandoffStrategy.LAUNCH_ONLY),
    )

    @Test
    fun `only installed recommended apps appear, plus tvprovider, recommended pinned first`() {
        val catalog = SourceCatalog.buildSourceCatalog(
            recommended = recommended,
            installedPackages = setOf("org.courville.nova", "com.netflix.ninja"),
            disabledIds = emptySet(),
            order = emptyList(),
        )

        // Nova + Netflix installed (Stremio hidden), then tvprovider last.
        assertThat(catalog.map { it.id }).containsExactly(
            "handoff:org.courville.nova",
            "handoff:com.netflix.ninja",
            SourceCatalog.TV_PROVIDER_ID,
        ).inOrder()

        // Recommended installed apps are pinned before the system source.
        assertThat(catalog.first().recommended).isTrue()
        assertThat(catalog.first().installed).isTrue()
        assertThat(catalog.last().id).isEqualTo(SourceCatalog.TV_PROVIDER_ID)
        assertThat(catalog.last().recommended).isFalse()
    }

    @Test
    fun `descriptor fields map correctly`() {
        val catalog = SourceCatalog.buildSourceCatalog(
            recommended = recommended,
            installedPackages = setOf("org.courville.nova"),
            disabledIds = emptySet(),
            order = emptyList(),
        )

        val nova = catalog.single { it.id == "handoff:org.courville.nova" }
        assertThat(nova.label).isEqualTo("Nova Player")
        assertThat(nova.packageName).isEqualTo("org.courville.nova")
        assertThat(nova.capability).isEqualTo(SourceCapability.HANDOFF_ONLY)
        assertThat(nova.enabled).isTrue()

        val tv = catalog.single { it.id == SourceCatalog.TV_PROVIDER_ID }
        assertThat(tv.packageName).isNull()
        assertThat(tv.capability).isEqualTo(SourceCapability.INLINE_RESULTS)
    }

    @Test
    fun `disabled id flips enabled to false but keeps the descriptor listed`() {
        val catalog = SourceCatalog.buildSourceCatalog(
            recommended = recommended,
            installedPackages = setOf("org.courville.nova"),
            disabledIds = setOf("handoff:org.courville.nova"),
            order = emptyList(),
        )
        val nova = catalog.single { it.id == "handoff:org.courville.nova" }
        assertThat(nova.enabled).isFalse()
    }

    @Test
    fun `saved order moves listed ids first, unlisted keep natural position after`() {
        val catalog = SourceCatalog.buildSourceCatalog(
            recommended = recommended,
            installedPackages = setOf("org.courville.nova", "com.netflix.ninja"),
            disabledIds = emptySet(),
            // User pinned tvprovider first; Netflix explicitly second; Nova unlisted.
            order = listOf(SourceCatalog.TV_PROVIDER_ID, "handoff:com.netflix.ninja"),
        )
        assertThat(catalog.map { it.id }).containsExactly(
            SourceCatalog.TV_PROVIDER_ID,        // ordered #0
            "handoff:com.netflix.ninja",         // ordered #1
            "handoff:org.courville.nova",        // unlisted -> natural position after ordered
        ).inOrder()
    }

    @Test
    fun `applySourceOrder with empty order is identity`() {
        val items = listOf("a", "b", "c")
        assertThat(SourceCatalog.applySourceOrder(items, emptyList()) { it }).isEqualTo(items)
    }

    @Test
    fun `applySourceOrder ignores order ids with no matching item`() {
        val items = listOf("a", "b")
        val result = SourceCatalog.applySourceOrder(items, listOf("b", "ghost", "a")) { it }
        assertThat(result).containsExactly("b", "a").inOrder()
    }

    @Test
    fun `selectEnabledOrdered drops disabled then orders survivors`() {
        val items = listOf("tvprovider", "handoff:x", "handoff:y")
        val result = SourceCatalog.selectEnabledOrdered(
            items = items,
            disabledIds = setOf("handoff:x"),
            order = listOf("handoff:y", "tvprovider"),
        ) { it }
        assertThat(result).containsExactly("handoff:y", "tvprovider").inOrder()
    }

    @Test
    fun `moveOrder moves an id up and down`() {
        val ids = listOf("a", "b", "c")
        assertThat(SourceCatalog.moveOrder(ids, "b", up = true)).containsExactly("b", "a", "c").inOrder()
        assertThat(SourceCatalog.moveOrder(ids, "b", up = false)).containsExactly("a", "c", "b").inOrder()
    }

    @Test
    fun `moveOrder is a no-op at edges or for missing id`() {
        val ids = listOf("a", "b", "c")
        assertThat(SourceCatalog.moveOrder(ids, "a", up = true)).isEqualTo(ids)
        assertThat(SourceCatalog.moveOrder(ids, "c", up = false)).isEqualTo(ids)
        assertThat(SourceCatalog.moveOrder(ids, "z", up = true)).isEqualTo(ids)
    }
}
