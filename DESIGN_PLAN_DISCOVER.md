# DESIGN PLAN — OpenTVSearch "Discover" home: launcher-independent rich content-app experience

**Status:** proposed · **Scope:** an engaging default/home surface for OpenTVSearch that is ALWAYS
populated, does not depend on the stock launcher or the TV-Provider tables, and makes launching /
searching your content apps a first-class, visual experience.

## 0. Why this shape (device-verified feasibility)

Measured on the Skyworth HP4609 (Android 14) on 2026-07-05:
- **TV-Provider tables are EMPTY** — `preview_programs`, `watch_next_programs`, and `channels` all
  return 0 rows. Root cause: the home was swapped to **FLauncher**, which does not consume
  recommendation channels, so the installed apps publish nothing. A "browse real content rows"
  view built on `READ_TV_LISTINGS` would render empty on this device.
- **What IS always available**, launcher-independent, no special permission, via `PackageManager`:
  - `getApplicationIcon(pkg)` — always present.
  - `getApplicationBanner(pkg)` — the wide 16:9 TV banner leanback apps declare (`android:banner`);
    present for TV apps, **null for some** → icon fallback required.
  - `getApplicationLabel(pkg)`.
  - the **LEANBACK_LAUNCHER launch intent** — confirmed resolvable for all installed content apps
    (SmartTube, Nova, Stremio, Netflix, Kan).
- We ALSO already have (from the search work) a device-tested per-app capability: **real search**
  (Nova, SmartTube) vs **launch-only** (Stremio, Netflix, Kan).

Decision (user-chosen): **skip TV-Provider for now; build the rich experience from data we can
always get.** This surface showcases the *apps* beautifully and puts search/launch one click away —
honest (it does not pretend to show in-app catalog content) and never empty.

> TV-Provider "Discover real content rows" remains a future enhancement: when present (stock
> launcher / a device that populates the tables), we can add a "Continue watching / Recommended"
> row above the app rail. This plan is additive to that, not a replacement.

## 1. The experience

A **Discover home** shown when OpenTVSearch opens with no active query (replacing today's empty
void under the search bar):

```
┌───────────────────────────────────────────────────────────────┐  27dp top overscan
│  [ 🔍  Search movies, shows, clips … ]  [ 🎤 ]  [ ⚙ ]          │  search bar (unchanged)
│                                                                 │
│  Your content apps                                              │  section header
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐            │  BANNER tiles (16:9),
│  │  [SmartTube  │ │   [Nova      │ │  [Stremio    │   …         │  focusable TvLazyRow,
│  │   banner]    │ │    banner]   │ │   banner]    │            │  scale+glow on focus
│  │ 🔍 Searchable│ │ 🔍 Searchable│ │  Opens app   │            │  capability chip
│  └──────────────┘ └──────────────┘ └──────────────┘            │
│                                                                 │
│  (when a query is active → the existing result grid replaces    │
│   this Discover home)                                           │
└───────────────────────────────────────────────────────────────┘  27dp bottom overscan
```

- **Banner tiles**, not icon dots: each installed content app rendered as its wide 16:9 banner
  (fallback: icon centered on a themed tile with the label) — this is what makes it feel like a
  real TV content surface rather than an app drawer.
- **Recommended content apps first** (the same RECOMMENDED registry, pinned), then any other
  installed leanback apps (optional, phase 2 of this surface).
- **Capability chip** on each tile: "🔍 Searchable" for apps with a verified query strategy
  (Nova, SmartTube), "Opens app" for launch-only (Stremio, Netflix, Kan). Sets honest expectations.
- **Click behavior:**
  - Searchable app + there's text in the search box → fire that app's real search with the query.
  - Otherwise (or launch-only app) → launch the app (leanback intent).
  - This means: type "matrix", then click the SmartTube tile → SmartTube opens to matrix results;
    click it with an empty box → SmartTube just opens. Intuitive and honest.
- **Focus:** reuse the TV focus treatment from result cards (scale 1.1×, brand border, NO
  white-on-white — pin container/content colors). First tile auto-focuses when Discover shows.

## 2. Components / data

### C1 — `InstalledContentApp` model + detector
Extend `InstalledAppDetector` (or a new `core/apps/ContentAppCatalog.kt`) to return, for each
installed RECOMMENDED app (and optionally other leanback apps):
```
data class InstalledContentApp(
    val packageName: String,
    val label: String,
    val recommended: Boolean,
    val searchable: Boolean,          // handoffStrategy carries a query (Nova/SmartTube) vs launch-only
    // visuals + launch resolved at the UI/repo layer via PackageManager:
    //   banner: Drawable? = pm.getApplicationBanner(pkg)   (nullable → icon fallback)
    //   icon:   Drawable  = pm.getApplicationIcon(pkg)
    //   launch: Intent    = pm.getLeanbackLaunchIntentForPackage(pkg) ?? getLaunchIntentForPackage
)
```
Keep the PURE mapping (registry + installed set → ordered `InstalledContentApp` list, recommended
first) unit-testable, mirroring `SourceCatalog`. Drawable loading stays in the Android layer.

### C2 — `DiscoverRow` composable (Compose for TV)
- A `TvLazyRow` of banner tiles under a "Your content apps" header.
- Each tile: a focusable TV `Surface` (pinned dark colors, focusedScale 1.1f, brand border) holding
  the banner `Drawable` (via Coil `rememberDrawablePainter` or `AsyncImage` with the Drawable) at
  16:9; fallback tile (icon + label) when banner is null; a small capability chip overlay.
- Honor D-pad: left/right through tiles; down from the search bar lands on the first tile.

### C3 — Wire into `SearchScreen`
- When `state.query` is blank AND `state.results` is empty → show `DiscoverRow` instead of the
  empty region. When a query is active → show the existing result grid (unchanged).
- Add an `onLaunchApp(packageName, withQuery: Boolean)` callback to the activity: if withQuery and
  the app is searchable and the box has text → build the app's search intent (reuse the SAME
  strategy logic as `DeepLinkHandoffSource` — do NOT duplicate it; expose a shared builder), else
  launch the leanback intent. Start via the activity context (add FLAG_ACTIVITY_NEW_TASK if needed).

### C4 — Reuse, don't duplicate, the hand-off intent logic
The per-app search/launch intent building already lives in `DeepLinkHandoffSource.buildIntent` +
`HandoffTarget`. Extract the intent-building into a small reusable function/object
(`HandoffIntentFactory`) that BOTH `DeepLinkHandoffSource` and the new Discover launch path call, so
Nova/SmartTube search and the launch-only fallbacks stay defined in exactly one place.

### C5 — Tests
- Pure catalog mapping: installed set + RECOMMENDED → ordered `InstalledContentApp` list,
  recommended pinned, `searchable` flag correct per strategy.
- The shared intent factory: given a target + query → correct intent (component search for Nova,
  URL for SmartTube, launch for launch-only). Keep existing tests green.

## 3. Acceptance (verify on the Skyworth HP4609)
- [ ] Launch OpenTVSearch with NO query → Discover home shows a row of **banner tiles** for the
      installed content apps (SmartTube, Nova, Stremio, Netflix, Kan), recommended pinned first,
      NOT an empty screen.
- [ ] Banners render (or clean icon+label fallback where a banner is null); no broken/blank tiles.
- [ ] Capability chip correct: Searchable on Nova/SmartTube, "Opens app" on Stremio/Netflix/Kan.
- [ ] Focus: first tile auto-focused, D-pad traverses, focused tile scaled+bordered, NO
      white-on-white, never clipped.
- [ ] Click SmartTube tile with "matrix" typed → SmartTube opens to matrix results. Click Stremio
      tile → Stremio just opens. (Reuses the verified strategies.)
- [ ] Typing a query still shows the result grid as before (Discover hides).

## 4. Non-goals
- No TV-Provider content rows in this plan (tables empty on this device; deferred, additive later).
- No new dependencies. No Leanback library. No change to search/aggregation ranking or the verified
  hand-off strategies (only extracted/reused).
- Not an app *drawer* replacement — only content apps (RECOMMENDED + optionally leanback apps),
  focused on the search/discover use case.

## 5. Files
- `core/apps/InstalledAppDetector.kt` (or new `ContentAppCatalog.kt`) — `InstalledContentApp` +
  pure ordering.
- `sources/handoff/DeepLinkHandoffSource.kt` — extract `HandoffIntentFactory` (shared builder).
- `ui/DiscoverRow.kt` (new) — banner-tile row composable.
- `ui/SearchScreen.kt` — show Discover when idle; wire launch/search-app callback.
- `ui/SearchActivity.kt` / `SearchViewModel.kt` — provide installed-content-apps state + the
  launch/search action.
- tests under `core/apps/` and `sources/handoff/`.
