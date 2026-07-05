# OpenTVSearch

Open-source universal content search for Android / Google TV. Type or **speak** a
query and find movies, shows, and clips across the content apps you actually use —
then jump straight into the app that has it. Remote-button-mappable, voice-first,
and fully configurable.

> Status: **v0.1 scaffold.** Architecture + entry points + the core `SearchSource`
> abstraction are in place; source adapters and the Compose-for-TV UI are stubbed
> with `TODO(v1)` markers. See [Roadmap](#roadmap).

## Platform reality (read this first)

There is **no** Android API that lets a sideloaded app read the *full internal
catalog* of a closed app like Netflix. That is a hard platform limit — confirmed
against AOSP source, not a design shortcut. What **is** supported, and what this app
is built on:

| Mechanism | What you get | Coverage |
|---|---|---|
| **TV Provider searchable rows** (`READ_TV_LISTINGS`, API 26+) | Real items other apps published to the home screen and marked `COLUMN_SEARCHABLE=1`: title, poster, play-deep-link, type | Whatever each app chooses to publish (recommendation / Watch Next rows) — **not** full catalogs |
| **Open-app APIs** (Jellyfin REST, Kodi JSON-RPC, Plex, Stremio addons) | True inline result lists from servers/addons the user configures | Full, for those apps |
| **Search deep-link hand-off** (`ACTION_SEARCH`, URL schemes) | Opens the target app **pre-filled** with the query | Any app with a search intent (Netflix, YouTube, Nova, ...) |

So OpenTVSearch is a **hybrid federated search + hand-off** tool: deep results where
the platform allows reading, one-press hand-off everywhere else. This is the best any
third-party app can do on the platform, and the app is honest about it in the UI
(INLINE vs HANDOFF results).

The stock Google TV search covers *Google's partner streamers* via server-side feeds;
it does **not** cover sideloaded/open apps (Stremio, Nova, Jellyfin, Kodi, self-hosted)
and isn't configurable. That gap is what this app fills.

## Architecture

Everything content can come from implements one interface — the seam the design is
built around:

```
core/search/
  SearchSource.kt      # the ONE extension point (id, label, capability, search())
  SearchResult.kt      # unified hit: INLINE (resolved item) or HANDOFF (opens app)
  SearchAggregator.kt  # parallel fan-out to all enabled sources, timeout, merge/rank

sources/
  tvprovider/  TvProviderSearchSource   # reads other apps' searchable rows (READ_TV_LISTINGS)
  jellyfin/    JellyfinSearchSource      # example open-app API adapter (/Search/Hints)
  handoff/     DeepLinkHandoffSource      # opens closed apps pre-filled with the query

core/apps/     InstalledAppDetector       # LEANBACK_LAUNCHER enumeration + recommended-app pinning
core/settings/ SettingsRepository         # DataStore prefs incl. "voice on launch"
ui/            SearchActivity (exported, searchable, voice) + SearchViewModel
```

Adding a new source = implement `SearchSource`, register it. That's it.

## Features (target v1)

- Type **or voice** search; **toggle** to auto-fire voice on app launch.
- Exported, searchable `SearchActivity` → mappable to a remote button (Button Mapper,
  or the system search key) via `ACTION_SEARCH`.
- Configurable source list; installed content apps auto-detected, recommended ones
  (Stremio, Netflix, Nova, Jellyfin, Kodi, YouTube, Plex) **pinned on top**.
- Real inline results from TV-Provider rows + configured open apps; deep-link hand-off
  for the rest.
- No telemetry. No crash-phone-home. Clean Apache-2.0.

## Roadmap

- [ ] `TvProviderSearchSource`: query `preview_programs` + `watch_next_programs`, map
      `COLUMN_INTENT_URI` → launch intent, feature-gate at API 26.
- [ ] Compose-for-TV `SearchScreen`: query field + D-pad result grid, INLINE/HANDOFF
      cards, `startActivity(result.launch)` on click.
- [ ] Wire `SearchViewModel` + sources via Hilt; voice-on-launch hook in `SearchActivity`.
- [ ] `JellyfinSearchSource` (`/Search/Hints`) and a Stremio addon source.
- [ ] Source-config + pinning settings UI backed by `InstalledAppDetector`.
- [ ] Verify each recommended app's hand-off strategy against a real installed APK.
- [ ] On-device D-pad QA.

## Build

Requires Android Studio + Android SDK (compileSdk 35). Open the project in Android
Studio — it will regenerate the Gradle wrapper (`gradlew` + `gradle-wrapper.jar`) and
sync automatically on first open. Then run the `app` config on an Android TV
device/emulator (API 26+ for the TV-Provider source).

```
./gradlew :app:assembleDebug     # build
./gradlew :app:testDebugUnitTest # unit tests (SearchAggregator ranking/dedup)
```

## License

Apache-2.0. Intentionally free of GPL-encumbered dependencies so anyone can install
and redistribute.
