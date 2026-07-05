# OpenTVSearch — Milestone 1 status & on-device QA

## Build status (verified by Hermes, not self-reported)
- `gradle :app:compileDebugKotlin` → **SUCCESS** (Kotlin + KSP + Hilt codegen clean).
- `gradle :app:testDebugUnitTest` → **SUCCESS** (SearchAggregatorTest + TvProviderSearchSourceTest pass).
- Environment: Gradle 8.9, ANDROID_HOME=/home/avi/android-sdk (android-35), `--no-daemon`.
- Note: the opencode dispatch's final test run failed on a Gradle *test-executor connection
  timeout* (infra flake under load), NOT a code error. Re-run with `--no-daemon` passes.
  opencode's LLM stream also crashed at the end (provider internalServerException), so its
  summary file was never written — this document replaces it.

## What was implemented (M1, all verified present + compiling)
- **T1** `TvProviderSearchSource` — queries preview_programs + watch_next_programs via
  TvContractCompat; API-26 gate; READ_TV_LISTINGS permission gate (returns empty, never throws);
  pure testable `mapRow()`/`mapType()`; skips blank-title & no-launch-intent rows; stable id.
- **T2** `di/AppModule.kt` — Hilt graph; source list = TvProvider + one DeepLinkHandoffSource
  per INSTALLED recommended app; rebuilt per query on Dispatchers.IO.
- **T3** `SearchViewModel` — @HiltViewModel; onQueryChange/submit/onVoiceResult/voiceOnLaunch().
- **T4** `SearchActivity` (@AndroidEntryPoint, runtime perms, ACTION_SEARCH handling, voice-on-
  launch) + `SearchScreen` (TvLazyVerticalGrid, first-result auto-focus, Coil posters,
  INLINE="PLAY"/HANDOFF="OPEN APP" badges) + `ui/theme/OpenTvSearchTheme`.
- **T5** `TvProviderSearchSourceTest` — pure unit tests (no Robolectric): valid→INLINE,
  blank-intent skip, blank-title skip, case-insensitive contains, stable id, type mapping.

## On-device QA checklist (requires a real Google TV — cannot be run in CI)
Build & install:
1. `gradle :app:assembleDebug` then `adb install -r app/build/outputs/apk/debug/app-debug.apk`
   (or Run from Android Studio on a Google TV device / API 26+ TV emulator).

Permissions & core search:
2. Launch from the TV app row. Confirm the app opens to the search screen.
3. Grant READ_TV_LISTINGS when prompted (Settings > Apps if it doesn't prompt).
4. Ensure an app that PUBLISHES searchable rows is installed & has content on the home
   screen (e.g. YouTube, Play Movies/Google TV, or any app with Watch Next rows).
5. Type a title you know is in those rows. Confirm INLINE ("PLAY") result cards appear with
   posters + titles.
6. D-pad down into the grid; confirm the first result is auto-focused and focus traverses.
7. Click an INLINE result → confirm it launches/plays in the owning app.

Hand-off:
8. Install at least one recommended app (Stremio / Nova / Jellyfin / Kodi / Netflix / YouTube / Plex).
9. Search anything; confirm an "OPEN APP" (HANDOFF) card appears for each installed recommended app.
10. Click a HANDOFF card → confirm the target app opens PRE-FILLED with the query.
    ⚠️ Verify each app's hand-off strategy against the ACTUAL installed APK — the strategies in
    InstalledAppDetector.RECOMMENDED (stremio:// URL, youtube results URL, ACTION_SEARCH, etc.)
    are best-effort and unverified against live APKs. Fix any that don't pre-fill.

Voice:
11. Tap the "🎤 Voice" button; grant RECORD_AUDIO; speak a title; confirm it searches.
12. Enable the voice-on-launch setting (currently a DataStore pref — no settings UI yet in M1;
    toggle via test hook or add it), relaunch; confirm the recognizer fires on open.

Remote-button mapping:
13. Using Button Mapper (or the system search key), fire an ACTION_SEARCH intent at the app;
    confirm SearchActivity opens and (if a query extra is present) searches immediately.

## Voice from remote
What now triggers the voice recognizer:
- **In-app mic button** — the "🎤 Voice" button in `SearchScreen` (existing).
- **Query-less search launch** — being launched via `ACTION_SEARCH`, GMS `SEARCH_ACTION`,
  or `ACTION_ASSIST` with NO query extra fires voice immediately (the remote asked to start
  a search, so we go straight to voice). A launch WITH a query still just runs that query.
- **`KEYCODE_SEARCH`** — the generic search key, while the app is foreground, fires voice.
- **Voice-on-launch pref** — a normal launcher open still fires voice only when the
  `voiceOnLaunch` DataStore pref is on (unchanged).

Voice fires **at most once** per `onCreate` (a single `shouldAutoLaunchVoice(...)` decision),
and never when a query is present.

**Platform caveat (hard limit):** the dedicated Assistant/mic button on stock Google TV
remotes is bound to Google Assistant at the SYSTEM level. No foreground app can intercept it
(`KEYCODE_ASSIST` / the Assistant button never reaches the app). The triggers above are the
only realistic ones a third-party app can receive.

**QA step:** map a remote button to "search" (Button Mapper) or otherwise fire `ACTION_SEARCH`
with NO query extra and confirm the speech recognizer opens on launch. Repeat with `ACTION_ASSIST`.

## Known M1 gaps (by design — next milestones)
- No settings UI yet for the voice-on-launch toggle or source enable/pin ordering
  (SettingsRepository + InstalledAppDetector.RECOMMENDED exist; UI is M2).
- Jellyfin/Plex/Kodi/Stremio API adapters still stubbed (return empty) — M2/M3.
- Hand-off strategies unverified against live APKs (see step 10).
- No app launcher art (placeholder vector banner only).
