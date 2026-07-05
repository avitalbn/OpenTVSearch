# CONTRACT BRIEF — OpenTVSearch M2: Settings screen (voice toggle + source management)

## Summary
Add a **Settings screen** to OpenTVSearch (Compose for TV) that lets the user:
1. Toggle **voice-on-launch** (persist the existing `SettingsRepository.voiceOnLaunch` pref via a
   real UI — the pref + read path already exist; there's just no screen to change it).
2. **Enable/disable which sources are searched**, and **reorder** them, with **recommended
   content apps pinned at the top** by default (the product vision: recommended apps like
   Stremio/Nova/Netflix/YouTube at the top of the list).

Then wire the enabled-set + ordering into the live search so disabling a source removes it from
results and reordering changes tie-break order.

This is a SELF-CONTAINED feature. Do NOT change the search/aggregation logic beyond reading the new
settings, do NOT touch the hand-off strategies, do NOT add new dependencies beyond what's already
on the classpath (Compose for TV, Hilt, DataStore, coroutines/Flow are all present). Read the
files below end-to-end before editing.

## Read first (context)
- `app/src/main/java/org/opentvsearch/core/settings/SettingsRepository.kt` — DataStore store;
  currently only `voiceOnLaunch: Flow<Boolean>` + `setVoiceOnLaunch`. EXTEND this.
- `app/src/main/java/org/opentvsearch/core/apps/InstalledAppDetector.kt` — `RECOMMENDED` registry
  (each `RecommendedApp` has packageName, label, handoffStrategy, recommended-by-being-here) +
  `installedLeanbackPackages()`.
- `app/src/main/java/org/opentvsearch/core/search/SearchSource.kt` — note the ALREADY-DEFINED
  `SourceDescriptor` data class (id, label, packageName, capability, recommended, installed,
  enabled). USE IT for the settings list model.
- `app/src/main/java/org/opentvsearch/di/AppModule.kt` — `buildEnabledSources(...)` builds the
  live list (TvProvider first, then a hand-off per installed recommended app). This is where the
  enabled-set + ordering filter must be applied.
- `app/src/main/java/org/opentvsearch/ui/SearchActivity.kt` and `SearchScreen.kt` — the search UI
  (Compose for TV). Add a way to open Settings from here.
- `app/src/main/java/org/opentvsearch/ui/theme/OpenTvSearchTheme.kt` — the TV Material 3 theme;
  reuse it for the Settings screen.

## Changes

### C1 — Persist source config in `SettingsRepository`
Add DataStore-backed, Flow-exposed prefs (keep `voiceOnLaunch` as-is):
- `disabledSourceIds: Flow<Set<String>>` (default empty = all enabled) + `setSourceEnabled(id,
  enabled)`. Store DISABLED ids (not enabled) so newly-appeared sources default to enabled.
- `sourceOrder: Flow<List<String>>` (default empty = use natural/recommended order) +
  `setSourceOrder(orderedIds: List<String>)` and/or `moveSource(id, up/down)`. Persist as an
  ordered list of source ids (e.g. a delimited string or JSON in a stringPreferencesKey).
- Source id convention: use the same ids the live sources use — `tvprovider` for
  `TvProviderSearchSource` (confirm its actual `id`), and `handoff:<packageName>` for hand-off
  sources (that's `DeepLinkHandoffSource.id`). Reordering/enabling must key off these exact ids so
  the AppModule filter matches.

### C2 — A source catalog for the settings list
Provide a way to enumerate candidate sources as `SourceDescriptor`s for the UI, combining:
- the fixed TvProvider source (recommended=false is fine, or mark it a system source shown first),
- one descriptor per `RECOMMENDED` app with `installed = packageName in installedLeanbackPackages()`
  and `recommended = true`,
- `enabled` computed from `disabledSourceIds`.
Put this in a small `SourcesCatalog`/repository class (or a function in `InstalledAppDetector` /
a new `core/sources/` file) — your call, keep it testable (pure mapping given the installed set +
disabled set + order). Recommended + installed sources sort to the TOP by default; not-installed
recommended apps may be shown greyed/disabled or hidden — prefer hidden for M2 to avoid confusion,
but if shown, mark clearly as "not installed".

### C3 — Apply enabled-set + order in `AppModule.buildEnabledSources`
- After building the candidate live sources, DROP any whose id is in `disabledSourceIds`.
- SORT the remaining by the persisted `sourceOrder` (ids not in the saved order keep their natural
  position after the ordered ones, or interleave sensibly — document the rule). Keep the
  AggregatorΓÇÖs existing INLINE-before-HANDOFF ranking; user order is the tie-break/among-HANDOFF
  order.
- `buildEnabledSources` currently isn't a suspend that reads settings — inject/So thread the
  `SettingsRepository` in and read the current values with `.first()` (it already runs on
  Dispatchers.IO and is rebuilt per query, so newly-changed settings apply on the next search).
  Do NOT block the main thread.

### C4 — Settings screen UI (Compose for TV)
- New `SettingsActivity` (exported=false) OR a Compose destination reachable from SearchScreen —
  simplest is a separate `@AndroidEntryPoint SettingsActivity` launched via an Intent. Use the
  existing `OpenTvSearchTheme`.
- Content, all D-pad navigable with visible TV focus (reuse the focus patterns from SearchScreen —
  TV `Surface`, scale/border on focus; NEVER white-on-white — pin container/content colors like
  the result cards do):
  - A **"Voice search on launch"** switch/toggle row bound to `voiceOnLaunch`.
  - A **sources list**: recommended+installed pinned at top, each row showing the app label, a
    capability hint ("Searches inside app" vs "Opens app"), an enable/disable toggle, and
    move-up/move-down affordances (D-pad friendly; a full drag isn't required — up/down buttons
    are fine and simpler on a remote).
- A `SettingsViewModel` (Hilt) exposing the descriptor list + toggle/move/setVoiceOnLaunch actions,
  writing through to `SettingsRepository`. Collect state with lifecycle-aware collection.
- Add an entry point from the search screen: a **"Settings" button** in the top Row of
  `SearchScreen` (next to Search/Voice) that launches Settings. Keep it D-pad reachable.

### C5 — Tests
- Unit-test the PURE parts: (a) the descriptor mapping (given installed set + disabled set + order
  → expected ordered/enabled descriptor list, recommended pinned first), and (b) the
  enabled+ordered filtering used by `buildEnabledSources` (extract it to a pure function so it's
  testable without Android). Keep existing tests green (`ShouldAutoLaunchVoiceTest`,
  `SearchAggregatorTest`, `TvProviderSearchSourceTest`).

## Constraints
- No new dependencies. No Leanback. Don't alter hand-off strategies or aggregation ranking.
- Everything must resolve colors from `OpenTvSearchTheme` (no white-on-white regressions).
- Settings changes take effect on the NEXT search (per-query source rebuild) — that's acceptable
  for M2; no need for live re-query.

## Verification (run to completion; report REAL output, do not fake)
```
export ANDROID_HOME=/home/avi/android-sdk ANDROID_SDK_ROOT=/home/avi/android-sdk
/home/avi/gradle-8.9/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon --console=plain
/home/avi/gradle-8.9/bin/gradle :app:assembleDebug --no-daemon --console=plain
```
Report verbatim BUILD result + test counts. If a TV Material API differs from what this brief
assumed, adapt to what compiles and note it. Verify the actual `id` values of TvProviderSearchSource
and DeepLinkHandoffSource before wiring settings keys to them.

When done, write `/tmp/opentvsearch_m2_done.md` with: files changed, how C1–C5 were implemented
(esp. the persisted-settings keys/format, the source-id convention used, and the Settings→Search
entry point), verbatim build/test output, and an HONESTY section listing anything skipped/faked/
worked-around or NOT verified (e.g. on-device — you have no device; I will verify on hardware).
Return that file's complete content.
