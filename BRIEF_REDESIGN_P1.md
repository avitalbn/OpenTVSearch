# CONTRACT BRIEF — OpenTVSearch: Phase-1 UI redesign + onNewIntent fix

## Summary
Fix the three concrete defects found on-device (Skyworth Google TV, 1920×1080) and add a small
correctness fix. This is a **surgical redesign of the existing search screen** using the TV
Material 3 components ALREADY on the classpath — **do NOT add any new dependency**, do NOT
introduce Leanback, do NOT migrate to sectioned rows (that's a later phase). Follow the full
design plan at `/home/avi/workspace/OpenTVSearch/DESIGN_PLAN_UI.md` — it is the contract; read it
end to end first.

Existing deps (already present, use these): `androidx.tv:tv-material:1.0.0`,
`androidx.tv:tv-foundation:1.0.0-alpha11`. Existing files to edit:
- `app/src/main/java/org/opentvsearch/ui/SearchScreen.kt`
- `app/src/main/java/org/opentvsearch/ui/theme/OpenTvSearchTheme.kt`
- `app/src/main/java/org/opentvsearch/ui/SearchActivity.kt`

Read all three BEFORE editing. Read `SearchViewModel.kt` too (do not change its public API unless
strictly required; if you add a method, keep it minimal). Reuse existing helpers.

---

## The exact changes

### C1 — Search text is invisible (BLOCKER)
Root cause: `SearchScreen.kt` uses the **mobile** `androidx.compose.material3.OutlinedTextField`
inside the **TV** `androidx.tv.material3.MaterialTheme`. The two libraries have separate color
trees, so the field's text/placeholder color is NOT resolved from the TV dark scheme → text ≈
background color.

Fix — make the field's colors come from the TV theme so text is clearly legible:
- **Preferred (TV-native):** replace the mobile `OutlinedTextField` with a focusable
  `androidx.tv.material3.Surface` wrapping a `androidx.compose.foundation.text.BasicTextField`.
  Set `textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface)`
  (TV MaterialTheme), placeholder text color = `onSurfaceVariant`, container color =
  `surfaceVariant`, and a `cursorBrush = SolidColor(colorScheme.primary)`. Give the Surface a
  visible focused state (border/glow) so the user can tell when the field is focused. Keep
  `singleLine`, `ImeAction.Search`, and the existing `onSubmit`/`onQueryChange` wiring intact,
  and preserve the "Search movies, shows, clips" placeholder text.
- **Acceptable fallback if the TV-native field is fiddly:** keep the mobile `OutlinedTextField`
  but wrap ONLY it in a nested `androidx.compose.material3.MaterialTheme(colorScheme =
  <explicit dark scheme with real onSurface/primary>) { ... }` so its internal color resolution
  is correct. Prefer the TV-native path; use this only if needed to stay in scope.

Acceptance: typed text AND placeholder are clearly legible (dark background, light text, ≥4.5:1)
in both focused and unfocused states.

### C2 — Cards oversized + focused card clipped at top (BLOCKER)
Root cause: `TvGridCells.Fixed(4)` + `ResultCard` `width(180.dp)` is too big for the 960dp TV
canvas; grid `contentPadding=4.dp` reserves NO room for focus scale, so the focused top-row card
(which scales up) overflows the top edge. No focus indicator is configured on the card.

Fix:
- Change grid to `TvGridCells.Fixed(5)` (roughly 5 across on the 960dp canvas with overscan
  margins). Remove the hard `width(180.dp)` on the card and let the grid cell drive width; keep
  the poster `aspectRatio(2f/3f)`.
- Change the root `Column` padding from flat `24.dp` to overscan-safe
  `PaddingValues(horizontal = 48.dp, vertical = 27.dp)`.
- Change the grid `contentPadding` to reserve scale room, e.g.
  `PaddingValues(top = 24.dp, bottom = 24.dp)` (plus small horizontal). Keep vertical/horizontal
  arrangement spacing ≥ 16.dp.
- Add a real focus indicator to `ResultCard`'s TV `Surface`:
  `scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)` and a focused border via
  `ClickableSurfaceDefaults.border(...)` (and optionally glow). Use the correct
  `androidx.tv.material3` APIs for the `tv-material:1.0.0` version on the classpath — verify the
  actual signatures before using them; if an API differs, adapt to what compiles.

Acceptance: ~5 cards per row, focused card is visibly scaled + bordered/glowing and is NEVER
clipped at the top or sides.

### C3 — Hand-off card placeholder quality (cosmetic, do with C2)
Hand-off/deep-link cards have no poster and currently show a bare first letter on `#2A2A2A`.
Replace with a branded placeholder: attempt to load the source app's icon via
`context.packageManager.getApplicationIcon(packageName)` when a package name is available on the
`SearchResult`; otherwise fall back to a source-colored tile with the centered source label.
If `SearchResult` does not currently expose the package name, DO NOT redesign the model — just
use the existing `sourceLabel`/`title` in a nicer centered tile (color from theme). Keep it in
scope.

### C4 — Theme upgrade (`OpenTvSearchTheme.kt`)
Replace the bare `darkColorScheme()` with an explicit TV `darkColorScheme(...)` that sets a
brand `primary` (violet), readable `onSurface`/`onSurfaceVariant`, and distinct `surface` vs
`surfaceVariant` so fields/cards separate from the background. Optionally add a Typography with
larger title/body sizes for 10-ft legibility. Keep it minimal and compiling.

### C5 — onNewIntent (correctness fix from last session)
Problem: when a SEARCH/ASSIST intent (with or without a query) is delivered to an ALREADY-RUNNING
`SearchActivity` instance, `onCreate` does not re-run, so the incoming query is dropped and the
voice auto-launch decision is not re-evaluated. On device this showed as
"intent delivered to currently running top-most instance" with the query ignored.

Fix in `SearchActivity.kt`:
- Extract the intent-handling logic currently in `onCreate` (compute `incomingQuery`, compute
  `isSearchIntent`, apply query to the viewModel + submit if non-blank, and run the
  `shouldAutoLaunchVoice(...)` decision → `startVoiceFlow()`) into a single private
  `handleSearchIntent(intent: Intent?)` function.
- Call it from `onCreate` (as today) AND override `onNewIntent(intent: Intent)`:
  `super.onNewIntent(intent); setIntent(intent); handleSearchIntent(intent)`.
- Keep the existing "voice fires at most once per intent" guard behavior sensible: a NEW intent
  is allowed to trigger voice again (that's the desired behavior for a repeated remote-search
  press), but do not double-fire within a single intent. Keep the pure `shouldAutoLaunchVoice`
  function and its existing unit test UNCHANGED.
- Do NOT change the manifest launchMode unless required; if a launchMode is needed for onNewIntent
  to fire reliably for these intents, prefer `singleTop` and note it. Verify onNewIntent is
  actually reached for `ACTION_SEARCH` delivered to a running instance.

---

## Constraints
- NO new dependencies. NO Leanback. NO sectioned-row/immersive migration (later phase).
- Do not refactor unrelated code. Keep `SearchViewModel` public API stable if possible.
- Keep the existing `ShouldAutoLaunchVoiceTest` green and unchanged. Add a small unit test only
  if you add a new pure function worth testing (e.g. a placeholder-selection helper); otherwise
  no new tests are required for UI changes.

## Verification (run to completion, report REAL output — do not fake)
```
export ANDROID_HOME=/home/avi/android-sdk ANDROID_SDK_ROOT=/home/avi/android-sdk
/home/avi/gradle-8.9/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon --console=plain
```
Then also confirm it assembles:
```
/home/avi/gradle-8.9/bin/gradle :app:assembleDebug --no-daemon --console=plain
```
Report the verbatim BUILD SUCCESSFUL/FAILED tail and the test counts. If anything fails, fix it
and re-run until green; if a TV Material API signature differs from what the brief assumed, adapt
to the real API and say what you changed.

When done, write `/tmp/opentvsearch_redesign_done.md` summarizing: which files changed, how each
of C1–C5 was implemented (esp. the exact TV field approach and the focus-indicator APIs used),
the verbatim build/test output, and an HONESTY section noting anything skipped, faked, or
worked-around. Return that file's complete content.
