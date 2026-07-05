package org.opentvsearch.core.apps

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.opentvsearch.sources.handoff.HandoffStrategy

class ContentAppCatalogTest {

    private val recommended = listOf(
        RecommendedApp("org.smarttube.stable", "SmartTube", HandoffStrategy.URL_TEMPLATE),
        RecommendedApp("org.courville.nova", "Nova Player", HandoffStrategy.ACTION_SEARCH_COMPONENT),
        RecommendedApp("com.stremio.one", "Stremio", HandoffStrategy.LAUNCH_ONLY),
        RecommendedApp("com.netflix.ninja", "Netflix", HandoffStrategy.LAUNCH_ONLY),
    )

    @Test
    fun `only installed recommended apps appear, in RECOMMENDED registry order`() {
        val apps = ContentAppCatalog.buildContentApps(
            recommended = recommended,
            installedPackages = setOf("com.netflix.ninja", "org.smarttube.stable", "org.courville.nova"),
        )

        // Registry order preserved (SmartTube, Nova, Netflix); Stremio not installed → omitted.
        assertThat(apps.map { it.packageName }).containsExactly(
            "org.smarttube.stable",
            "org.courville.nova",
            "com.netflix.ninja",
        ).inOrder()
        assertThat(apps.all { it.recommended }).isTrue()
    }

    @Test
    fun `searchable flag is true for query-carrying strategies, false for launch-only`() {
        val apps = ContentAppCatalog.buildContentApps(
            recommended = recommended,
            installedPackages = recommended.map { it.packageName }.toSet(),
        ).associateBy { it.packageName }

        assertThat(apps.getValue("org.smarttube.stable").searchable).isTrue()  // URL_TEMPLATE
        assertThat(apps.getValue("org.courville.nova").searchable).isTrue()    // ACTION_SEARCH_COMPONENT
        assertThat(apps.getValue("com.stremio.one").searchable).isFalse()      // LAUNCH_ONLY
        assertThat(apps.getValue("com.netflix.ninja").searchable).isFalse()    // LAUNCH_ONLY
    }

    @Test
    fun `no installed recommended apps yields empty list`() {
        val apps = ContentAppCatalog.buildContentApps(
            recommended = recommended,
            installedPackages = emptySet(),
        )
        assertThat(apps).isEmpty()
    }

    @Test
    fun `label is carried through from the registry`() {
        val apps = ContentAppCatalog.buildContentApps(
            recommended = recommended,
            installedPackages = setOf("org.courville.nova"),
        )
        assertThat(apps.single().label).isEqualTo("Nova Player")
    }
}
