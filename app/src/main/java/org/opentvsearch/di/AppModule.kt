package org.opentvsearch.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opentvsearch.core.apps.InstalledAppDetector
import org.opentvsearch.core.search.SearchAggregator
import org.opentvsearch.core.search.SearchSource
import org.opentvsearch.core.settings.SettingsRepository
import org.opentvsearch.sources.handoff.DeepLinkHandoffSource
import org.opentvsearch.sources.handoff.HandoffTarget
import org.opentvsearch.sources.tvprovider.TvProviderSearchSource
import javax.inject.Singleton

/**
 * Wires the M1 object graph.
 *
 * The enabled source list for Milestone 1 is:
 *   1. [TvProviderSearchSource] (real INLINE results from other apps' searchable rows), then
 *   2. one [DeepLinkHandoffSource] per INSTALLED recommended app (a HANDOFF entry that opens
 *      the app pre-filled with the query).
 *
 * Order matters only as a tie-breaker: the [SearchAggregator] already ranks INLINE ahead of
 * HANDOFF, then by source index — so TvProvider first keeps inline hits on top, followed by
 * hand-off targets in the curated RECOMMENDED order.
 *
 * The source list is rebuilt on every query (via the suspend provider) so newly installed or
 * removed content apps are picked up without an app restart.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)

    @Provides
    @Singleton
    fun provideInstalledAppDetector(@ApplicationContext context: Context): InstalledAppDetector =
        InstalledAppDetector(context)

    @Provides
    @Singleton
    fun provideTvProviderSearchSource(@ApplicationContext context: Context): TvProviderSearchSource =
        TvProviderSearchSource(context)

    @Provides
    @Singleton
    fun provideSearchAggregator(
        tvProvider: TvProviderSearchSource,
        detector: InstalledAppDetector,
    ): SearchAggregator = SearchAggregator(
        sourcesProvider = { buildEnabledSources(tvProvider, detector) },
    )

    /**
     * Builds the live source list: Tv-Provider first, then a hand-off target for each installed
     * recommended app. The package enumeration touches [android.content.pm.PackageManager], so it
     * runs on [Dispatchers.IO].
     */
    private suspend fun buildEnabledSources(
        tvProvider: TvProviderSearchSource,
        detector: InstalledAppDetector,
    ): List<SearchSource> = withContext(Dispatchers.IO) {
        val installed = runCatching { detector.installedLeanbackPackages() }
            .getOrDefault(emptyList())
            .toSet()

        val handoffSources = InstalledAppDetector.RECOMMENDED
            .filter { it.packageName in installed }
            .map { app ->
                DeepLinkHandoffSource(
                    HandoffTarget(
                        packageName = app.packageName,
                        label = app.label,
                        strategy = app.handoffStrategy,
                        urlTemplate = app.urlTemplate,
                    )
                )
            }

        buildList {
            add(tvProvider)
            addAll(handoffSources)
        }
    }
}
