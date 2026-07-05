# CONTRACT BRIEF — OpenTVSearch: remote search-button → voice trigger

## Summary
Make OpenTVSearch launch its voice recognizer when the app is opened via a remote
"search" affordance with NO query, and when the physical SEARCH key is pressed — so
"remote search button → voice input" works as far as the platform allows. Small,
surgical change to ONE activity + a manifest intent-filter addition + tests + docs.
Do NOT refactor anything else.

## Repository / workdir
`/home/avi/workspace/OpenTVSearch` (this is your workdir). Kotlin, Compose-for-TV,
Hilt. The app builds & tests green today via:
`ANDROID_HOME=/home/avi/android-sdk /home/avi/gradle-8.9/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon --console=plain`

## Platform reality (DO NOT try to beat this — it's a hard limit)
- The dedicated **mic/Assistant button** on stock Google TV remotes is hard-bound to
  Google Assistant at the SYSTEM level. No foreground app can intercept it. Do NOT
  attempt to capture `KEYCODE_ASSIST`/the Assistant button — it won't reach the app.
  Do not add hacky accessibility services or anything of that sort.
- What an app CAN receive: (a) being launched via `Intent.ACTION_SEARCH` (and the GMS
  `SEARCH_ACTION`, and `ACTION_ASSIST`) by a launcher/remote-mapper; (b) `KEYCODE_SEARCH`
  key events on remotes/mappers that emit them. Those are the only realistic triggers.
- Scope this change to exactly those receivable triggers. Anything more is out of scope.

## Current relevant code (read before editing)
- `app/src/main/java/org/opentvsearch/ui/SearchActivity.kt` — `@AndroidEntryPoint`
  ComponentActivity. `onCreate` currently: reads `incomingQuery` from `ACTION_SEARCH`/
  GMS `SEARCH_ACTION` (via `SearchManager.QUERY`); if non-blank → `onQueryChange` +
  `submit()`; requests `READ_TV_LISTINGS`; if `incomingQuery` blank AND
  `viewModel.voiceOnLaunch()` → `startVoiceFlow()`. Existing helpers you MUST reuse:
  `startVoiceFlow()` (checks RECORD_AUDIO, launches recognizer), `launchVoice()`,
  `voiceLauncher` (routes result to `viewModel.onVoiceResult`).
- `app/src/main/java/org/opentvsearch/ui/SearchViewModel.kt` — has `onQueryChange`,
  `submit`, `onVoiceResult`, `suspend voiceOnLaunch()`. Do not change its API.
- `app/src/main/AndroidManifest.xml` — `SearchActivity` is exported+searchable with
  intent-filters for LEANBACK_LAUNCHER/LAUNCHER, `ACTION_SEARCH`, and GMS `SEARCH_ACTION`.
- `app/src/main/res/xml/searchable.xml` — declares `voiceSearchMode`, but note it is
  INERT here because the UI is custom Compose (does not use the SearchManager dialog).
  Leave the file as-is; do not rely on it to trigger voice.

## Required change
### C1 — auto-launch voice on a query-less search launch (SearchActivity.onCreate)
Introduce a single decision. Define "launched as a search intent" =
`intent?.action in { Intent.ACTION_SEARCH, "com.google.android.gms.actions.SEARCH_ACTION",
Intent.ACTION_ASSIST }`.
- If launched as a search intent AND `incomingQuery` is NON-blank → existing behavior
  (seed query + submit). Keep as-is.
- If launched as a search intent AND `incomingQuery` IS blank → **auto-fire
  `startVoiceFlow()`** (the remote asked to start a search; go straight to voice).
- If NOT launched as a search intent (normal launcher open) → keep the existing
  voice-on-launch behavior (fire `startVoiceFlow()` only when `viewModel.voiceOnLaunch()`).
- Guard against double-firing voice: it must launch AT MOST once in `onCreate`. Factor
  the "should we auto-launch voice now?" decision into a single boolean so voice-on-launch
  and search-intent-empty don't both fire. Do not fire voice at all when there is a query.
- Also add `Intent.ACTION_ASSIST` to the `incomingQuery` action set where the query is
  read (harmless; some launchers use it).

### C2 — handle KEYCODE_SEARCH (SearchActivity)
Override `onKeyDown(keyCode, event)`: on `KeyEvent.KEYCODE_SEARCH`, call `startVoiceFlow()`
and return true; else `super.onKeyDown(...)`. (This covers remotes/mappers that emit the
generic search key while the app is foreground.)

### C3 — manifest: accept ACTION_ASSIST
Add an `<intent-filter>` to `SearchActivity` for `android.intent.action.ASSIST` +
`category.DEFAULT`, mirroring the existing GMS SEARCH_ACTION filter. This lets assistant-
style "search this app" launches reach us. Keep all existing filters.

## Hard rules
- Reuse existing `startVoiceFlow()`/`launchVoice()`/`voiceLauncher`; do NOT duplicate the
  recognizer plumbing.
- Do NOT change `SearchViewModel`'s public API, `SearchScreen`, the sources, or DI.
- Do NOT add analytics, accessibility services, GPL deps, or attempt to grab the Assistant button.
- Voice must fire at most once per `onCreate`; never when a query is present.
- Everything compiles as valid Kotlin; keep the existing tests green.
- Do NOT dispatch sub-agents. Do NOT exit waiting for callbacks.

## Tests
Add/adjust a small unit test for the pure decision logic. Since `onCreate` is Android-bound,
factor the decision into a pure testable function, e.g. a top-level/internal
`fun shouldAutoLaunchVoice(isSearchIntent: Boolean, hasQuery: Boolean, voiceOnLaunchPref: Boolean): Boolean`
in the ui package, and unit-test its truth table:
- searchIntent + no query → true
- searchIntent + query → false
- not searchIntent + voiceOnLaunch=true + no query → true
- not searchIntent + voiceOnLaunch=false → false
Wire `SearchActivity.onCreate` to call this function (so the test covers real logic, not a copy).
Keep `SearchAggregatorTest` and `TvProviderSearchSourceTest` green.

## Verification (run to completion; report honestly)
```
export ANDROID_HOME=/home/avi/android-sdk ANDROID_SDK_ROOT=/home/avi/android-sdk
/home/avi/gradle-8.9/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon --console=plain
```
Expect BUILD SUCCESSFUL + tests pass. If a Gradle test-executor connection timeout occurs
(known infra flake), re-run once; report the real outcome — do NOT fake success.

## Docs
Append a short note to `MILESTONE_1_STATUS.md` under a new "Voice from remote" subsection:
what now triggers voice (in-app mic button; query-less ACTION_SEARCH/ASSIST launch;
KEYCODE_SEARCH; voice-on-launch pref), and the explicit platform caveat that the dedicated
Assistant/mic button is system-bound to Google Assistant and cannot be intercepted by any app.
Add a QA step: map a remote button to "search"/fire ACTION_SEARCH with no query and confirm
the recognizer opens.

## Output
Write a summary to `/tmp/opentvsearch_voice_done.md` and return its complete content:
(1) exact edits per C1–C3, (2) the test truth table + result, (3) verbatim build/test output,
(4) files changed. Be honest about any step that didn't complete.
