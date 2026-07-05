# OpenTVSearch

**Universal, voice-first content search for Android TV / Google TV.**

OpenTVSearch is an open-source app that lets you search *across the content apps you already have
installed* — Stremio, Nova, Netflix, YouTube, Jellyfin, Kodi, Plex and more — from one screen,
mappable to a remote button and driven by voice. Instead of opening each app and searching
separately, you type or speak once and OpenTVSearch aggregates what it can and hands off to the
right app for the rest.

> Status: **early / Milestone 1.** Core search, aggregation, voice, and a TV-native UI work and
> are verified on real hardware (Skyworth HP4609, Android 14). Settings UI and richer per-app
> integrations are in progress — see [Roadmap](#roadmap).

---

## Why

Google TV's built-in search only surfaces results from Google's *partner* apps (Netflix, YouTube,
Prime…). Community and self-hosted apps (Stremio, Nova, Jellyfin, Kodi, SmartTube…) are invisible
to it, and there's no user-configurable way to add them. OpenTVSearch is the open alternative: it
reads what the platform *does* expose across all apps, and falls back to a clean per-app hand-off
for the rest — no root, no partner agreement, installable by anyone.

## Platform reality (read this first)

There is **no** Android API that lets a sideloaded app read the *full internal catalog* of a
closed app like Netflix. That is a hard platform limit — confirmed against AOSP source, not a
design shortcut. OpenTVSearch is therefore a **hybrid federated-search + hand-off** tool: deep
results where the platform allows reading, one-press hand-off everywhere else. What's actually
supported:

| Mechanism | What you get | Coverage |
|---|---|---|
| **TV Provider searchable rows** (`READ_TV_LISTINGS`, API 26+) | Items other apps published to the home screen: title, poster, play deep-link, type | Whatever each app publishes (recommendation / Watch Next rows) — **not** full catalogs |
| **Open-app APIs** (Jellyfin REST, Kodi JSON-RPC, Plex, Stremio addons) | True inline result lists from servers/addons the user configures | Full, for those apps |
| **Search deep-link hand-off** (`ACTION_SEARCH`, URL schemes) | Opens the target app — with the query where the app honors it, else just launched | Any installed app; query support is per-app (see below) |

The app is honest about this split in the UI: **INLINE** results are playable deep-links; **HANDOFF**
results open an app.

## What it does today (Milestone 1)

- **One search box, many sources.** A query fans out in parallel to every enabled source with a
  per-source timeout, and results are merged (playable/INLINE results ranked ahead of hand-offs).
- **Cross-app content via `READ_TV_LISTINGS`.** Reads the TV Provider `preview_programs` /
  `watch_next_programs` tables — the same home-screen recommendation rows Google TV's own search
  uses — so results carry real posters and play deep-links where apps publish them.
- **Honest per-app hand-off.** For apps whose catalog can't be read, OpenTVSearch offers a card
  that opens that app. Where an app genuinely honors an external query (e.g. **Nova**, via its
  search activity) the card runs a real in-app search; where it doesn't, the card is labeled
  *"Open X to search"* rather than pretending. (See [App support](#app-support).)
- **Voice-first, remote-mappable.** Launch it via a remote "search" button or the system search
  key and it opens straight into voice input; an on-screen mic works too. An optional
  *voice-on-launch* setting fires the recognizer as soon as the app opens.
- **TV-native UI.** Built with Compose for TV (Material 3 for TV): overscan-safe layout,
  D-pad focus with scale + border indicators, dark theme tuned for 10-foot viewing.

## App support

Hand-off behavior is **device-tested**, not assumed — an app advertising a search intent in its
manifest often ignores an externally-supplied query. Verified on Skyworth HP4609 (Android 14):

| App | External search | Behavior |
|-----|-----------------|----------|
| **Nova Video Player** (`org.courville.nova`) | ✅ Real search | Opens its query browser with your term and shows library results |
| **SmartTube** (`org.smarttube.stable`) | ✅ Real search | Opens a full "Search results for &lt;query&gt;" YouTube page (via the `vnd.youtube://results` scheme, package-pinned) |
| **Stremio** (`com.stremio.one`) | ⚠️ Launch-only | Declares `ACTION_SEARCH` + `stremio://search` but ignores the query (JS-internal search); opens to home. Card = *"Open Stremio to search"* |
| **Netflix** (`com.netflix.ninja`) | ⚠️ Launch-only | No `ACTION_SEARCH`, no `/search` deep-link; card opens the app |
| **כאן 11 / Kan** (`com.applicaster.il.ch1`) | ⚠️ Launch-only | Applicaster app; only `MAIN`, no search intent/scheme; card opens the app |
| **YouTube TV** (`com.google.android.youtube.tv`) | ⚠️ Launch-only | No external search intent; `vnd.youtube` search scheme is claimed by SmartTube when both are installed |
| **Jellyfin / Kodi / Plex** | ⚠️ Launch-only* | Conservative default until verified on a device |
| **Any app publishing TV-Provider rows** | ✅ via `READ_TV_LISTINGS` | Real posters + play deep-links, no per-app code needed |

\* If you verify a working query-carrying strategy for one of these on real hardware, PRs welcome —
see [Contributing](#contributing).

## Requirements

- Android TV / Google TV device or emulator, **Android 8.0 (API 26)+**.
- The app requests two runtime permissions: `READ_TV_LISTINGS` (to read other apps'
  recommendation rows) and `RECORD_AUDIO` (for voice search). Both are optional to grant; the app
  degrades gracefully without them.

## Build & install

```bash
# Prereqs: Android SDK, JDK 17. Set ANDROID_HOME.
export ANDROID_HOME=/path/to/android-sdk

# Build the debug APK
./gradlew :app:assembleDebug

# Install to a connected device (USB or network ADB)
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
```

Then launch it from the TV launcher, or fire a search directly:

```bash
# Query-carrying search (seeds results immediately)
adb shell am start -a android.intent.action.SEARCH \
  -n org.opentvsearch/.ui.SearchActivity --es query "your query"

# Query-less launch (opens straight into voice input)
adb shell am start -a android.intent.action.SEARCH -n org.opentvsearch/.ui.SearchActivity
```

To map it to a remote button, use a remapper (e.g. Button Mapper) to fire an `ACTION_SEARCH`
intent at `org.opentvsearch/.ui.SearchActivity`. Note: the dedicated Assistant/mic button on a
stock Google TV remote is system-bound to Google Assistant and **cannot** be intercepted by any
app — use a remappable button or the generic search key.

## Architecture

```
SearchActivity (Compose for TV)
  └─ SearchViewModel ── SettingsRepository (DataStore: voiceOnLaunch)
       └─ SearchAggregator  (parallel fan-out + per-source timeout + merge/rank)
            ├─ TvProviderSearchSource     READ_TV_LISTINGS → preview/watch_next rows (INLINE)
            ├─ DeepLinkHandoffSource(×N)  per installed recommended app (HANDOFF)
            └─ JellyfinSearchSource       (optional server source, WIP)
```

- **`SearchSource`** — common interface (`search(query): List<SearchResult>`, a capability flag).
- **`SearchResult`** — `kind = INLINE` (playable deep-link) or `HANDOFF` (opens an app).
- **`InstalledAppDetector.RECOMMENDED`** — curated registry mapping each known content app to a
  device-tested hand-off strategy; recommended apps are pinned to the top of the source list.
- **`HandoffStrategy`** — `URL_TEMPLATE`, `ACTION_SEARCH_COMPONENT` (targets a specific search
  activity), `ACTION_SEARCH`, `GMS_SEARCH_ACTION`, or `LAUNCH_ONLY`.

DI is Hilt; async is Kotlin coroutines/Flow; UI is Jetpack Compose for TV.

## Roadmap

- [x] M1 — cross-app search core, TV-Provider source, honest hand-off, voice, TV UI
- [ ] **M2 — Settings screen**: toggle voice-on-launch, enable/disable sources, reorder/pin apps
- [ ] Sectioned result rows (per-source `TvLazyRow`) + immersive header
- [ ] App-icon posters on hand-off cards
- [ ] More verified per-app search strategies (community-contributed)

## Contributing

PRs and issues welcome. The most valuable contributions right now are **device-tested per-app
search strategies**: pick an app, probe it (`adb shell dumpsys package <pkg> | grep -i search`),
fire a query (`am start -a android.intent.action.SEARCH -n <pkg>/<activity> --es query "test"`),
**screenshot to confirm the query actually lands** (not just that the app opened), and add/adjust
its entry in `InstalledAppDetector.RECOMMENDED` with a note on what you verified.

Please keep the honesty rule: never ship a query-carrying strategy that hasn't been confirmed on
real hardware. A wrong strategy that silently opens an app to its home screen is worse than an
honest launch-only card.

## License

[MIT](LICENSE).
