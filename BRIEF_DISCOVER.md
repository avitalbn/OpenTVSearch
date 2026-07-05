# CONTRACT BRIEF — OpenTVSearch Discover home (rich content-app tiles)

## Summary
Build a **Discover home** for OpenTVSearch: when the app opens with NO active query, show a
horizontal row of **banner tiles** for the installed content apps (recommended pinned first),
instead of today's empty region. Each tile shows the app's TV banner (icon+label fallback) and a
capability chip; clicking it either fires that app's real search (if searchable + query typed) or
launches the app. This is launcher-independent and always-populated.

Follow the design plan at `/home/avi/workspace/OpenTVSearch/DESIGN_PLAN_DISCOVER.md` — read it END
TO END first; it is the contract and explains WHY (the TV-Provider tables are empty on this device
because FLauncher replaced the stock home, so we build from PackageManager data that's always
available).

Read these before editing: `core/apps/InstalledAppDetector.kt`,
`sources/handoff/DeepLinkHandoffSource.kt`, `core/search/SearchSource.kt`, `ui/SearchScreen.kt`,
`ui/SearchActivity.kt`, `ui/SearchViewModel.kt`, `ui/theme/OpenTvSearchTheme.kt`,
`core/sources/SourceCatalog.kt` (for the pure-mapping style to mirror).

## Changes (C1–C5 in the plan)

### C1 — Installed-content-app catalog (pure + Android layers)
- Add `InstalledContentApp(packageName, label, recommended, searchable)` where `searchable` =
  the app's `HandoffStrategy` carries a query (ACTION_SEARCH_COMPONENT or URL_TEMPLATE) vs
  LAUNCH_ONLY. Nova + SmartTube are searchable; Stremio/Netflix/Kan/YouTube are not.
- Add a PURE function (mirror `SourceCatalog`): given `RECOMMENDED` + installed package set →
  ordered `List<InstalledContentApp>` (recommended pinned first, RECOMMENDED registry order).
  Put it in a new `core/apps/ContentAppCatalog.kt` (object) so it's unit-testable without Android.
- Drawable/intent resolution stays in the Android layer (a small repo/helper the ViewModel or UI
  uses): banner via `packageManager.getApplicationBanner(pkg)` (NULLABLE → icon fallback), icon via
  `getApplicationIcon(pkg)`, launch via `getLeanbackLaunchIntentForPackage(pkg)` falling back to
  `getLaunchIntentForPackage(pkg)`.

### C2 — Shared hand-off intent factory (reuse, don't duplicate)
- Extract the intent-building currently inside `DeepLinkHandoffSource.buildIntent` into a reusable
  `HandoffIntentFactory` (object/function) taking a `HandoffTarget` + query and returning the
  Intent. `DeepLinkHandoffSource` calls it; the Discover launch path calls it too. This keeps the
  verified per-app strategies (Nova component search, SmartTube vnd.youtube URL, launch-only) in
  ONE place. Do not change strategy behavior.

### C3 — `DiscoverRow` composable (Compose for TV)
- New `ui/DiscoverRow.kt`: a "Your content apps" header + a `TvLazyRow` of banner tiles.
- Each tile = focusable TV `Surface` with pinned dark colors (containerColor/focusedContainerColor
  = surfaceVariant, matching content colors — NO white-on-white), `focusedScale = 1.1f`, brand
  focused border (same pattern as ResultCard). Content: the banner Drawable at 16:9 (use Coil's
  `com.google.accompanist.drawablepainter.rememberDrawablePainter` IF already available, else load
  the Drawable into an `AsyncImage`/`Image` — verify what's on the classpath; if neither, render the
  icon Drawable + label as the tile and skip banners rather than add a dependency). Fallback tile
  (icon centered + label) when banner is null. Small capability chip: "Searchable" vs "Opens app".
- Auto-focus the first tile when Discover is shown.

### C4 — Wire into SearchScreen + Activity
- In `SearchScreen`: when `state.query` is blank AND `state.results` is empty → render `DiscoverRow`
  in place of the empty area; otherwise render the existing result grid (unchanged). Pass the
  installed-content-apps list + an `onLaunchApp(packageName, withQuery: Boolean)` callback.
- In `SearchActivity`: implement the launch action — if the app is searchable and the query box is
  non-blank, build its search intent via `HandoffIntentFactory` and start it; else start the
  leanback launch intent. Start from the activity (add FLAG_ACTIVITY_NEW_TASK if required). Handle a
  null/again-unresolvable intent gracefully (Toast, no crash).
- `SearchViewModel`: expose the installed-content-apps as state (load once, off main thread via the
  detector). Keep it simple; the list can be loaded in `init`/on first show.

### C5 — Tests
- Pure `ContentAppCatalog` ordering + `searchable` flag correctness.
- `HandoffIntentFactory`: Nova → ACTION_SEARCH to QueryBrowserActivityVideo w/ query; SmartTube →
  VIEW vnd.youtube://results?search_query=<enc> pkg-pinned; launch-only → the launch/main intent.
- Keep ALL existing tests green (ShouldAutoLaunchVoiceTest, SearchAggregatorTest,
  TvProviderSearchSourceTest, SourceCatalogTest).

## Constraints
- No new dependencies UNLESS `rememberDrawablePainter` (accompanist) is already on the classpath —
  check first; if not present, do NOT add it, use the Drawable via the existing Coil/Image path or
  fall back to icon tiles. Report what you chose.
- No Leanback library. No change to search ranking or verified hand-off strategies (only extracted).
- All colors from `OpenTvSearchTheme`; no white-on-white; overscan-safe; D-pad focus obvious.
- Discover shows only when idle; typing a query must still show the result grid exactly as now.

## Verification (run to completion; REAL output, do not fake)
```
export ANDROID_HOME=/home/avi/android-sdk ANDROID_SDK_ROOT=/home/avi/android-sdk
/home/avi/gradle-8.9/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon --console=plain
/home/avi/gradle-8.9/bin/gradle :app:assembleDebug --no-daemon --console=plain
```
Report verbatim BUILD result + test counts. Verify actual APIs before using them
(`getApplicationBanner` return type, TV `TvLazyRow`/`items` import path, whether a drawable-painter
is available). Adapt to what compiles and note deviations.

When done write `/tmp/opentvsearch_discover_done.md`: files changed, how C1–C5 were implemented
(esp. how banners are rendered + the drawable-painter decision, and the shared HandoffIntentFactory),
verbatim build/test output, and an HONESTY section listing anything skipped/faked/worked-around or
NOT verified (you have no device — I verify banners/focus/launch on hardware). Return its content.
