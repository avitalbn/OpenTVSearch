# CONTRACT BRIEF — OpenTVSearch Milestone 1 (make it work end-to-end on a device)

## Summary
Implement the first working slice of **OpenTVSearch**, an open-source Android/Google TV
universal content-search app. The scaffold already exists at the repo root; your job is
to fill in the `TODO(v1)` stubs so the app **actually runs on a Google TV device**:
a user (typing or by voice) searches, sees a D-pad-navigable grid of results pulled from
**other installed apps' searchable TV-Provider rows** plus **deep-link hand-off** entries,
and clicking a result launches into the owning app.

This is Milestone 1 of a build-fresh project (we audited and rejected forking ARVIO).
Do the smallest set of changes that makes the end-to-end path real and testable. Do NOT
gold-plate. Open-app API adapters (Jellyfin/Plex/Kodi/Stremio) are OUT OF SCOPE this
milestone (leave those stubs returning emptyList).

## Repository / workdir
- Repo root: `/home/avi/workspace/OpenTVSearch` (this is your workdir).
- Kotlin, Jetpack **Compose for TV** (`androidx.tv:tv-material`), Hilt DI, DataStore,
  Coil, minSdk 23 / compileSdk 35. Package root `org.opentvsearch`.
- Read `README.md` first — it states the architecture and the hard platform limits. Do
  not violate the "Platform reality" section.

## Read order (existing files — read before writing)
1. `README.md` — architecture + platform constraints.
2. `app/src/main/java/org/opentvsearch/core/search/SearchSource.kt` — the seam. `SearchSource`
   (`id`, `label`, `capability`, `suspend fun search(query): List<SearchResult>`),
   `SourceCapability {INLINE_RESULTS, HANDOFF_ONLY}`, `SourceDescriptor`.
3. `core/search/SearchResult.kt` — `SearchResult(id,title,subtitle?,posterUri:Uri?,type,
   sourceId,sourceLabel,kind,launch:Intent)`, `ContentType`, `ResultKind {INLINE,HANDOFF}`.
4. `core/search/SearchAggregator.kt` — parallel fan-out, per-source timeout, inline-first
   ranking, dedup by `"$sourceId:$id"`. Already implemented + unit-tested. Do not change its
   public API without updating `app/src/test/.../SearchAggregatorTest.kt`.
5. `sources/tvprovider/TvProviderSearchSource.kt` — **STUB to implement (primary task)**.
6. `sources/handoff/DeepLinkHandoffSource.kt` — implemented; `HandoffTarget`,
   `HandoffStrategy {URL_TEMPLATE, ACTION_SEARCH, GMS_SEARCH_ACTION}`. Reuse as-is.
7. `core/apps/InstalledAppDetector.kt` — `installedLeanbackPackages()` + `RECOMMENDED` list
   of `RecommendedApp(packageName,label,handoffStrategy,urlTemplate?)`.
8. `core/settings/SettingsRepository.kt` — `voiceOnLaunch: Flow<Boolean>` + setter.
9. `ui/SearchActivity.kt` + `ui/SearchViewModel.kt` — **STUBS to implement**.
10. `app/src/main/AndroidManifest.xml` — SearchActivity is exported+searchable; permissions
    `READ_TV_LISTINGS`, `RECORD_AUDIO`, and `<queries>` already declared.

## AOSP-verified facts you MUST rely on (do not re-research, do not contradict)
- Reading other apps' content is possible ONLY via the TV Provider tables
  `preview_programs` and `watch_next_programs`, using the runtime permission
  `android.permission.READ_TV_LISTINGS`. Confirmed against AOSP `TvProvider.java`: the read
  path for these tables is NOT package-filtered; a granted caller reads rows across apps,
  scoped effectively to `package_name = caller OR searchable = 1`. So you get **other apps'
  rows that were published with COLUMN_SEARCHABLE=1**.
- These tables + `READ_TV_LISTINGS` require **API 26 (O)+**. Feature-gate: below O, or if the
  permission is not granted, `TvProviderSearchSource.search()` returns `emptyList()`.
- Use the AndroidX compat wrappers already on the classpath: `androidx.tvprovider:tvprovider`
  → `androidx.tvprovider.media.tv.TvContractCompat`, `PreviewProgram`, `WatchNextProgram`.
  Query `TvContractCompat.PreviewPrograms.CONTENT_URI` and
  `TvContractCompat.WatchNextPrograms.CONTENT_URI`.
- Useful columns per row (map into `SearchResult`):
  `COLUMN_TITLE` → title, `COLUMN_POSTER_ART_URI` → posterUri (parse to Uri),
  `COLUMN_INTENT_URI` → the launch deep-link (parse with `Intent.parseUri(uri,
  Intent.URI_INTENT_SCHEME)`; if null/blank, SKIP the row — a result with no launch target
  is useless), `COLUMN_TYPE` → map to `ContentType` (TYPE_MOVIE→MOVIE, TYPE_TV_SERIES→
  TV_SERIES, TYPE_TV_EPISODE→TV_EPISODE, TYPE_CLIP→CLIP, TYPE_CHANNEL→CHANNEL,
  TYPE_TRACK→TRACK, else UNKNOWN), `COLUMN_INTERNAL_PROVIDER_ID`/`_ID` → contributes to `id`.

## Tasks (implement, in order)

### T1 — `TvProviderSearchSource.search(query)` (PRIMARY)
- Guard: `if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()`.
- Guard: if `READ_TV_LISTINGS` not granted (`ContextCompat.checkSelfPermission`), return
  `emptyList()` (the UI layer requests the permission; the source must never crash).
- Query BOTH `PreviewPrograms.CONTENT_URI` and `WatchNextPrograms.CONTENT_URI` via
  `context.contentResolver.query(...)`. Read title, poster, intent-uri, type, package/id.
- Filter in-memory: keep rows whose `title` contains `query` case-insensitively (the provider
  has no good server-side LIKE contract across OEMs — do a client-side contains match).
- Skip rows with blank title or unparseable/blank `COLUMN_INTENT_URI`.
- Map to `SearchResult(kind = ResultKind.INLINE, sourceId = id, sourceLabel = label, ...)`.
  Build a launch `Intent` from `COLUMN_INTENT_URI`.
- `id` must be stable + unique per row (e.g. `"${packageName}:${internalProviderId ?: rowId}"`)
  so aggregator dedup works.
- Close the Cursor (use `.use { }`). Run on `Dispatchers.IO`. Be cancellation-cooperative.
- Never throw for expected failures (missing provider, SecurityException) — catch → emptyList.

### T2 — Source wiring via Hilt (`di/AppModule.kt` — NEW FILE)
- Create `@Module @InstallIn(SingletonComponent::class) object AppModule`.
- Provide: `SettingsRepository`, `InstalledAppDetector`, `TvProviderSearchSource`, and a
  `SearchAggregator` whose `sourcesProvider` returns the enabled source list.
- For M1 the source list = `[TvProviderSearchSource]` + one `DeepLinkHandoffSource` per
  INSTALLED recommended app (cross-reference `InstalledAppDetector.installedLeanbackPackages()`
  against `InstalledAppDetector.RECOMMENDED`; build a `HandoffTarget` from each match). This
  gives real inline results where available + hand-off for installed content apps. Order:
  TvProvider first, then handoff targets (aggregator already ranks INLINE ahead of HANDOFF).
- Provide `@ApplicationContext` where needed. Add `@HiltViewModel` + `@Inject constructor`
  to `SearchViewModel` (currently a plain constructor — convert it).

### T3 — `SearchViewModel` finalize
- Convert to `@HiltViewModel class SearchViewModel @Inject constructor(private val
  aggregator: SearchAggregator, private val settings: SettingsRepository) : ViewModel()`.
- Keep the existing `onQueryChange`/`submit`/`SearchUiState` shape. Add `voiceOnLaunch`
  exposure (read once) so the Activity can decide whether to auto-launch voice.
- Add `fun onVoiceResult(text: String)` that sets query + calls `submit()`.

### T4 — `SearchActivity` + Compose-for-TV `SearchScreen` (NEW `ui/SearchScreen.kt`)
- `SearchActivity`: `@AndroidEntryPoint`. In `onCreate`:
  - Request `READ_TV_LISTINGS` (and `RECORD_AUDIO` when voice used) at runtime via
    `registerForActivityResult(RequestPermission/RequestMultiplePermissions)`. Re-run search
    after grant.
  - Handle incoming `ACTION_SEARCH` / GMS `SEARCH_ACTION` query extra
    (`SearchManager.QUERY`) → seed the ViewModel and submit immediately.
  - `setContent { OpenTvSearchTheme { SearchScreen(...) } }`.
  - If `voiceOnLaunch` is true AND there is no incoming query, fire the existing
    `launchVoice()` recognizer on first launch; route its result via
    `viewModel.onVoiceResult(...)`.
- `SearchScreen` (Compose for TV): a text field (D-pad focusable) with a mic button that
  triggers voice, a loading indicator, an error line, and a **`TvLazyVerticalGrid`** of
  result cards. Each card shows poster (Coil `AsyncImage`), title, subtitle, a small badge
  distinguishing INLINE vs HANDOFF, and the `sourceLabel`. On click:
  `context.startActivity(result.launch)` wrapped in runCatching (some deep links may fail;
  show a toast on failure). Ensure the first result is auto-focused for remote use.
- Add `ui/theme/OpenTvSearchTheme.kt` (NEW) — a minimal TV Material dark theme wrapper.

### T5 — Tests + verification
- Add `TvProviderSearchSourceTest` (Robolectric OR pure unit with a fake ContentResolver via
  a small seam) covering: SDK<O → empty; permission-denied → empty; a fake cursor with 2 rows
  (one with blank intent-uri that must be skipped, one valid) → 1 mapped INLINE result with the
  right title/type and a non-null launch intent; title filter is case-insensitive contains.
  If Robolectric is too heavy, refactor the cursor→SearchResult mapping into a pure internal
  function `mapRow(...)` and unit-test THAT directly. Prefer the pure-function refactor.
- Keep the existing `SearchAggregatorTest` green.

## Hard rules
- Do NOT add analytics, crash reporting, Sentry/Firebase, cloud sync, or any network call
  except what an in-scope source needs (M1 sources make NO network calls). This is a
  privacy-clean OSS tool.
- Do NOT pull in GPL dependencies (no CloudStream lineage). Apache/MIT deps only.
- Do NOT implement the Jellyfin/Plex/Kodi/Stremio API adapters this milestone — leave stubs.
- Do NOT change `SearchAggregator`'s public API or `SearchSource`/`SearchResult` shape
  unless strictly necessary; if you must, update the tests and note it in the done file.
- Do NOT dispatch sub-agents. Do NOT exit waiting for callbacks.
- Feature-gate all TV-Provider + READ_TV_LISTINGS code at API 26; the app must still install
  and run (degraded, handoff-only) on API 23–25.
- Every file compiles as valid Kotlin; imports resolve; no unresolved references.

## Verification commands (run what you can; the reviewer will re-run)
- `./gradlew :app:testDebugUnitTest` — unit tests pass (aggregator + tvprovider mapping).
- `./gradlew :app:assembleDebug` — app compiles. NOTE: if the Android SDK is not installed in
  this environment and the build cannot fetch it, DO NOT fake success — report exactly which
  step failed and how far compilation got (e.g. Kotlin compile of `:app` vs SDK download).
  Static correctness (imports, signatures) is still required.
- `./gradlew lintDebug` if it runs without network.

## Anti-patterns (cause rejection)
- Reporting "builds successfully" without having actually run gradle to completion.
- A `TvProviderSearchSource` that throws on missing permission/provider instead of returning
  empty.
- Results with null/blank launch intents surfacing in the grid.
- Blocking the main thread on the ContentResolver query.
- Editing the manifest to drop `exported=true` or the searchable/SEARCH intent-filters.
- Adding telemetry or GPL deps.

## On-device QA (document as a manual checklist in the done file — cannot run here)
List the exact steps to verify on a real Google TV: install, grant READ_TV_LISTINGS, ensure
an app that publishes searchable rows is present (e.g. YouTube/Play Movies), search a known
title, confirm inline results appear with posters, confirm clicking launches the owning app,
confirm a handoff card opens an installed content app pre-filled, confirm the voice mic works,
confirm voice-on-launch toggle behavior, confirm D-pad focus traversal.

## Output
Write a summary to `/tmp/opentvsearch_m1_done.md` and return its complete content. Include:
(1) what you implemented per task, (2) exact build/test command results (verbatim, honest —
including any SDK-unavailable failure), (3) any API changes you had to make + why, (4) the
on-device QA checklist, (5) list of files created/modified.
