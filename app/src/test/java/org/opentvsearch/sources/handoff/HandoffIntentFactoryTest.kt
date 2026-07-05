package org.opentvsearch.sources.handoff

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Verifies the PURE [HandoffIntentFactory.spec] branching — the per-app strategy decisions. We
 * assert on the framework-free [HandoffIntentSpec] rather than a live Intent because the JVM unit
 * test runs against the stub android.jar (returnDefaultValues), which cannot round-trip an Intent's
 * action/component/data/extras. Query encoding is exercised via the injectable `encode` lambda so
 * the produced URL is deterministic without Android's `Uri.encode`.
 */
class HandoffIntentFactoryTest {

    private fun target(
        strategy: HandoffStrategy,
        urlTemplate: String? = null,
        searchActivity: String? = null,
        packageName: String = "com.example.app",
        label: String = "Example",
    ) = HandoffTarget(packageName, label, strategy, urlTemplate, searchActivity)

    @Test
    fun `Nova - ACTION_SEARCH_COMPONENT targets the search activity with the query`() {
        val spec = HandoffIntentFactory.spec(
            target(
                strategy = HandoffStrategy.ACTION_SEARCH_COMPONENT,
                searchActivity = "com.archos.mediacenter.video.browser.QueryBrowserActivityVideo",
                packageName = "org.courville.nova",
            ),
            query = "matrix",
        )
        assertThat(spec).isEqualTo(
            HandoffIntentSpec.ComponentSearch(
                packageName = "org.courville.nova",
                activity = "com.archos.mediacenter.video.browser.QueryBrowserActivityVideo",
                query = "matrix",
            )
        )
    }

    @Test
    fun `SmartTube - URL_TEMPLATE produces the vnd-youtube results url, package-pinned`() {
        val spec = HandoffIntentFactory.spec(
            target(
                strategy = HandoffStrategy.URL_TEMPLATE,
                urlTemplate = "vnd.youtube://results?search_query={q}",
                packageName = "org.smarttube.stable",
            ),
            query = "matrix",
            encode = { it },
        )
        assertThat(spec).isEqualTo(
            HandoffIntentSpec.ViewUrl(
                packageName = "org.smarttube.stable",
                url = "vnd.youtube://results?search_query=matrix",
            )
        )
    }

    @Test
    fun `URL_TEMPLATE applies the encoder to the query`() {
        val spec = HandoffIntentFactory.spec(
            target(strategy = HandoffStrategy.URL_TEMPLATE, urlTemplate = "app://s?q={q}"),
            query = "the matrix",
            encode = { it.replace(" ", "%20") },
        )
        assertThat(spec).isEqualTo(
            HandoffIntentSpec.ViewUrl(packageName = "com.example.app", url = "app://s?q=the%20matrix")
        )
    }

    @Test
    fun `LAUNCH_ONLY produces a Launch spec regardless of query`() {
        val spec = HandoffIntentFactory.spec(
            target(strategy = HandoffStrategy.LAUNCH_ONLY, packageName = "com.stremio.one"),
            query = "matrix",
        )
        assertThat(spec).isEqualTo(HandoffIntentSpec.Launch(packageName = "com.stremio.one"))
    }

    @Test
    fun `ACTION_SEARCH is package-scoped and carries the query`() {
        val spec = HandoffIntentFactory.spec(
            target(strategy = HandoffStrategy.ACTION_SEARCH, packageName = "com.x"),
            query = "matrix",
        )
        assertThat(spec).isEqualTo(HandoffIntentSpec.ActionSearchPackage("com.x", "matrix"))
    }

    @Test
    fun `GMS_SEARCH_ACTION carries the query`() {
        val spec = HandoffIntentFactory.spec(
            target(strategy = HandoffStrategy.GMS_SEARCH_ACTION, packageName = "com.x"),
            query = "matrix",
        )
        assertThat(spec).isEqualTo(HandoffIntentSpec.GmsSearch("com.x", "matrix"))
    }

    @Test
    fun `missing required data yields null spec`() {
        assertThat(
            HandoffIntentFactory.spec(target(strategy = HandoffStrategy.ACTION_SEARCH_COMPONENT), "q")
        ).isNull()
        assertThat(
            HandoffIntentFactory.spec(target(strategy = HandoffStrategy.URL_TEMPLATE), "q")
        ).isNull()
    }
}
