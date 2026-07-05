package org.opentvsearch.core.search

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SearchAggregatorTest {

    private fun result(source: String, kind: ResultKind, id: String = "1") =
        SearchResult(
            id = id, title = "t", sourceId = source, sourceLabel = source,
            kind = kind, launch = Intent(),
        )

    private fun source(sourceId: String, results: List<SearchResult>) =
        object : SearchSource {
            override val id = sourceId
            override val label = sourceId
            override val capability = SourceCapability.INLINE_RESULTS
            override suspend fun search(query: String) = results
        }

    @Test
    fun `empty query returns nothing`() = runTest {
        val agg = SearchAggregator({ listOf(source("a", listOf(result("a", ResultKind.INLINE)))) })
        assertThat(agg.search("   ")).isEmpty()
    }

    @Test
    fun `inline results ranked before handoff`() = runTest {
        val agg = SearchAggregator({
            listOf(
                source("handoff", listOf(result("handoff", ResultKind.HANDOFF))),
                source("inline", listOf(result("inline", ResultKind.INLINE))),
            )
        })
        val out = agg.search("batman")
        assertThat(out.first().kind).isEqualTo(ResultKind.INLINE)
        assertThat(out.last().kind).isEqualTo(ResultKind.HANDOFF)
    }

    @Test
    fun `results deduped by source and id`() = runTest {
        val agg = SearchAggregator({
            listOf(source("a", listOf(
                result("a", ResultKind.INLINE, id = "x"),
                result("a", ResultKind.INLINE, id = "x"),
            )))
        })
        assertThat(agg.search("q")).hasSize(1)
    }
}
