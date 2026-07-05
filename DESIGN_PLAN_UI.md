# OpenTVSearch — UI/UX Design Plan (Google TV / Compose for TV)

**Status:** proposed · **Scope:** visual/interaction redesign of the search screen
**Framework:** Compose for TV (Material 3 for TV) — *already a project dependency*, we just
need to use it correctly. No new UI framework is introduced.

---

## 0. Why this plan (grounded in what the device showed)

Two concrete defects seen on the Skyworth HP4609 (1920×1080, Android 14) and confirmed in code:

| # | Symptom (on device) | Root cause (in code) |
|---|---------------------|----------------------|
| **D1** | Search-box text is the same color as the background → invisible | `SearchScreen.kt:78` uses **mobile** `androidx.compose.material3.OutlinedTextField` inside the **TV** `androidx.tv.material3.MaterialTheme`. The two libraries have separate `colorScheme` / `LocalContentColor` trees, so the mobile field resolves text color from an unthemed mobile scheme, not the TV dark scheme. |
| **D2** | Result cards are huge; the focused top-row card is clipped at the top | `SearchScreen.kt:110` `TvGridCells.Fixed(4)` + card `width(180.dp)` is oversized for the 960dp design canvas; grid `contentPadding=4.dp` reserves **no room for focus scale** (TV cards scale 1.05–1.1× on focus), so the enlarged focused card overflows the top edge. No focus indicator (scale/border/glow) is configured on the card `Surface` either, so focus is nearly invisible. |

Everything below is measured against Google's **official TV design specs** (developer.android.com
/design/ui/tv), not folklore.

---

## 1. Design canvas & the numbers we design to (official)

Source: *Layouts | TV* and *Focus system | TV* (developer.android.com).

- **Design at 960 × 540 dp** (MDPI, 1px = 1dp). The system scales to 720p/1080p/4K. All dp
  values below are on this canvas.
- **Overscan-safe margins:** **48 dp** left/right, **27 dp** top/bottom (≈5%). Use the safe
  variant **58 dp sides / 28 dp top-bottom** for primary content. *Nothing important lives
  outside this.*
- **12-column grid:** columns 52 dp wide, **20 dp** gutters, 58 dp side margins.
- **Focus indicators (the heart of TV UX):** scale **1.025 / 1.05 / 1.1×**, glow **2–32 dp**,
  optional border/outline, and **surface/content color change** on focus (tonal elevation +1..+5).
- **Framework:** Compose for TV (`androidx.tv.material3` + `androidx.tv.foundation.lazy`).
  Leanback is **deprecated** — do not reintroduce it. We already depend on
  `androidx.tv:tv-material:1.0.0` and `androidx.tv:tv-foundation:1.0.0-alpha11`.

---

## 2. Screen layout (target)

```
┌───────────────────────────────────────────────────────────────┐  ← 27dp top overscan
│  [ 🔍  Search movies, shows, clips …            ]  [ 🎤 ]      │  search bar (focusable row)
│                                                                 │  48/58dp side margins
│  Movies · Shows · Clips        (optional filter chips, later)   │
│                                                                 │
│   ┌──────┐   ┌──────┐   ┌──────┐   ┌──────┐   ┌──────┐          │  result row(s)
│   │poster│   │poster│   │poster│   │poster│   │poster│  …       │  cards scale on focus,
│   │      │   │      │   │      │   │      │   │      │          │  extra top/side pad
│   └──────┘   └──────┘   └──────┘   └──────┘   └──────┘          │  reserves scale room
│   Title      Title      Title      Title      Title             │
│   Source     Source     Source     Source     Source            │
│                                                                 │
└───────────────────────────────────────────────────────────────┘  ← 27dp bottom overscan
```

- **Root padding:** replace the flat `padding(24.dp)` with **`PaddingValues(horizontal = 48.dp,
  vertical = 27.dp)`** (overscan-safe) on the root `Column`.
- **Search bar:** stays a top `Row`. Text field takes `weight(1f)`; voice mic is an icon-only
  circular button (56 dp touch/focus target) on the right; the "Search" text button is optional
  once IME-action-search + voice cover the flows.

---

## 3. Fixes, in priority order

### FIX-A — Search text visibility (D1) — **blocker**
Swap the mobile text field for the **TV** field so it inherits the TV dark scheme.

- Prefer **`androidx.tv.material3`**'s text entry pattern. TV Material 3 does **not** ship a
  full `OutlinedTextField`; the idiomatic TV pattern is a focusable **`Surface`** wrapping a
  `BasicTextField`, with colors pulled from `MaterialTheme.colorScheme` (TV) so text =
  `onSurface`, placeholder = `onSurfaceVariant`, container = `surfaceVariant`/`surface`.
- Explicitly set `textStyle = LocalTextStyle.current.copy(color = colorScheme.onSurface)` and a
  `cursorBrush` in the TV color — never rely on the cross-library default.
- Give the field a **visible focus state**: border/glow when focused (a TV `Surface` gives this
  for free via `ClickableSurfaceDefaults` / `SurfaceDefaults`).
- **Acceptance:** typed text and placeholder are both clearly legible (≥ 4.5:1 contrast) on the
  dark surface, in both focused and unfocused states; verified by screenshot on device.

> Alternative if we want to keep a Material `OutlinedTextField` for speed: wrap it in a nested
> **mobile** `androidx.compose.material3.MaterialTheme` whose `colorScheme` is a dark scheme with
> explicit `onSurface`/`primary`, so its internal color resolution is correct. This is a
> stop-gap; the TV-native `Surface + BasicTextField` is the recommended end state.

### FIX-B — Card sizing + focus-scale clipping (D2) — **blocker**
- **Size cards for 10-foot viewing, not phone density.** Use a **2:3 poster ~104 × 156 dp**
  (roughly two 12-grid columns wide incl. gutter). Across a 960dp canvas minus 58dp margins ×2
  that yields **~5 cards per row** with 20dp gutters — matches the grid spec and reads well at
  distance. (Current 180dp × Fixed(4) is oversized.)
- Switch the grid columns to **`TvGridCells.Fixed(5)`** *or* a fixed card width in a
  `TvLazyRow` per content section (see §4).
- **Reserve room for focus scale.** The focused card scales up to **1.1×**; the container must
  not clip it. Do this by:
  - increasing grid **`contentPadding`** to at least **`PaddingValues(top = 24.dp, bottom =
    24.dp, horizontal = 8.dp)`** so a scaled top-row card doesn't cross the top edge, and
  - keeping `verticalArrangement`/`horizontalArrangement` spacing ≥ 16 dp so neighbors don't
    overlap when one scales.
- **Add a real focus indicator** to `ResultCard`'s `Surface` via
  `ClickableSurfaceDefaults.scale(focusedScale = 1.1f)` + `.border(focused = <border>)` +
  optional `.glow(...)`. TV UX *requires* an obvious focused state.
- **Acceptance:** focused card is fully visible (never clipped top/side), visibly scaled + glows/
  bordered, ~5 across, no overlap; verified on device with the focused card in the top row.

### FIX-C — Poster placeholder quality (cosmetic, do with B)
The deep-link/hand-off cards have no artwork (only TV-Provider rows carry posters), so today they
show a bare letter "S" on `#2A2A2A`. Replace with a proper **branded placeholder**: app icon (via
`PackageManager.getApplicationIcon(pkg)`) or a source-colored tile + centered app name, so
result cards look intentional rather than broken.

### FIX-D — Typography & content color for 10-foot legibility
- Use TV Material 3 type scale; bump result titles to `titleMedium`, source label
  `bodyMedium`/`labelLarge`. Minimum body text ~ **18–24 sp** effective at 10 ft.
- All text colors from the **TV** `colorScheme` (`onSurface`, `onSurfaceVariant`) — never
  hard-coded `Color(...)` except intentional brand accents (the KIND badge is fine to keep
  colored, but pull its text color from theme).

### FIX-E — Empty / loading / error states (polish)
- **Idle (no query):** center a friendly hero ("Search across your apps — press 🎤 or start
  typing") plus, later, the "recommended apps" row (M2). Avoids a blank void on launch.
- **Loading:** keep the spinner but center it in the result region, not inline at top.
- **Error / no-results:** center with an icon, don't leave it as a lone top-left line.

---

## 4. Layout pattern choice — grid vs. sectioned rows

Google TV's own home and Google's TV design guidance favor **horizontal rows grouped by
category** (the *Immersive list* / row pattern) over a dense uniform grid, because D-pad
navigation is axis-based (left/right within a group, up/down between groups).

**Recommendation (phased):**
- **Phase 1 (this plan):** keep a single `TvLazyVerticalGrid` but fix sizing/focus/colors
  (FIX-A/B). Fastest path to a correct-looking screen.
- **Phase 2 (M2):** migrate to **sectioned `TvLazyRow`s** — one row per source/kind
  ("From Stremio", "From Nova", "Play now" for INLINE TV-Provider rows), each row horizontally
  scrollable. This matches the platform's mental model and scales when many apps return results.
  Optionally add an **Immersive header** that previews the focused item's poster/metadata at the
  top (the official *Immersive list* component).

---

## 5. Theme work (`OpenTvSearchTheme.kt`)

Today it's a bare `darkColorScheme()`. Upgrade to a deliberate palette so contrast is guaranteed
and focus/tonal-elevation reads correctly:

- Define an explicit TV **`darkColorScheme(...)`** with a brand `primary` (the violet already
  seen), readable `onSurface`/`onSurfaceVariant`, and distinct `surface` vs `surfaceVariant` so
  cards/fields separate from the background.
- Provide **shapes** and **typography** overrides tuned for 10-ft (larger type, generous corner
  radius on cards, e.g. 12–16 dp).
- Everything downstream then pulls from theme — no per-composable `Color(0xFF…)` for structural
  UI.

---

## 6. Acceptance checklist (verify on the Skyworth HP4609 via ADB)

Re-run the install→screenshot loop after each fix; a screenshot with the **focused card in the
top row** is the key evidence for D2.

- [ ] **A**: search text + placeholder clearly legible on dark surface, focused & unfocused.
- [ ] **B**: ~5 cards/row, correct 2:3 poster proportion, focused card scaled + bordered/glowing
      and **never clipped** at top or sides.
- [ ] **C**: hand-off cards show app-icon/branded placeholder, not a bare letter.
- [ ] **D**: titles/labels legible at 10 ft; all structural colors from theme.
- [ ] **E**: idle/loading/error/no-results states centered and intentional.
- [ ] Overscan: no primary element within 48dp (sides) / 27dp (top-bottom) of the edge.
- [ ] D-pad: from search field, DOWN lands on first card; LEFT/RIGHT traverse cards; focus is
      always obvious.

---

## 7. Files touched

- `app/src/main/java/org/opentvsearch/ui/SearchScreen.kt` — text field swap (A), grid sizing +
  focus padding + card focus indicator (B), placeholder (C), typography (D), states (E).
- `app/src/main/java/org/opentvsearch/ui/theme/OpenTvSearchTheme.kt` — explicit colorScheme,
  typography, shapes (§5).
- (Phase 2) new `components/ResultRow.kt` / `ImmersiveHeader.kt` for the sectioned-row migration.

## 8. Non-goals (this plan)
- No new architecture, no data-layer changes, no new dependencies (TV Material already present).
- Poster *fetching* for hand-off apps beyond the app-icon placeholder is out of scope (needs
  per-app metadata; M2+).
- Sectioned-row / immersive-header migration is Phase 2 — flagged, not built here.

---

### Source citations (official)
- Layouts | TV — 960×540dp canvas, 48/27dp overscan, 12-col × 52dp / 20dp gutters:
  https://developer.android.com/design/ui/tv/guides/styles/layouts
- Focus system | TV — scale 1.025/1.05/1.1×, glow 2–32dp, border/outline, tonal surface:
  https://developer.android.com/design/ui/tv/guides/styles/focus-system
- Immersive list | TV — row + dynamic preview pattern (Phase 2):
  https://developer.android.com/design/ui/tv/guides/components/immersive-list
- Compose for TV (Jetpack) — androidx.tv.material3 / androidx.tv.foundation:
  https://developer.android.com/jetpack/androidx/releases/tv
