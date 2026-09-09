# Media — MethodMesh v0.4.8

`media` is a no-user-sign-in Media module built around three distinct layers:

1. **Catalogue** — downloadable/importable work + provider availability metadata.
2. **Personal library** — MethodMesh-owned favourites, watchlist, watched state, tags and notes.
3. **Capture / discovery** — manual/observed media capture and ambient audio identification.

Target: MethodMesh Master Book v1.07 (2026-09-08).

## Capability inventory

| ID | Purpose | Offline | Interactive |
|---|---|---:|---:|
| `media.catalogue.search` | Search imported catalogue with entitlement filters | yes | optional |
| `media.catalogue.import` | Catalogue Sources: refresh from publishers; CSV/JSON remains advanced fallback | yes | yes (file picker) |
| `media.library.list` | Browse favourites/watchlist/watched, optionally by provider | yes | optional |
| `media.library.state` | Set local favourite/watchlist/watched state | yes | optional |
| `media.library.capture` | Capture a structured media record | yes | optional |
| `media.audio.identify` | Identify ambient music from a short microphone sample | no | yes |

All six methods remain independently exposed to Dashboard/direct native use, Presets, Protocols and ODK through the canonical MethodMesh contract. The richer screens are not alternate implementations.




## v0.4.8 same-screen Now Playing result

- Ambient song recognition no longer replaces the listening surface with a separate result state.
- The circular listen control remains present after a match and becomes the obvious `Listen again` action.
- Identified title, artist and album appear directly beneath the listener on the same capability screen.
- The song title itself is the clipboard target; tapping it copies the practical `Title — Artist` result.
- The separate `Tap the title to copy` instruction/control has been removed.
- Preset/protocol/ODK structured outputs and canonical method IDs are unchanged.

## v0.4.7 audio result disambiguation

- Audio recognition keeps AudD's `title`, `artist` and `album` fields distinct.
- The native result hierarchy is now explicit: the recognised **song title** is the headline, the **artist** is immediately beneath it, and album metadata is labelled `Album · …` rather than appearing as an unlabeled accent value.
- Preset/log `media_value` remains `Title — Artist`; structured `media_title`, `media_creator` and `media_album` remain independently available.
- This is a presentation hardening change only; canonical method IDs and ODK contracts are unchanged.

## v0.4.6 browse-first catalogue and preset audio result

- TV/film opens in **Browse A–Z** rather than forcing a search-first workflow. Search remains available as the direct lookup mode.
- A–Z shelves apply the same service, entitlement, type, genre, language and runtime filters as search.
- Native browse/search consolidates multiple provider availability rows for the same `work_id` into one visible title with its services listed together.
- Service chips are denser and stay close to the catalogue rather than dominating the screen.
- `media.audio.identify` now places the human result (`media_value = "Title — Artist"`) first in the observation field order. MethodMesh preset logging chooses the first practical CORE field, so now-playing logs record the identified song instead of `media_operation: audio_identify`. Structured `media_title`, `media_creator` and operational fields remain unchanged for protocols/ODK.


## v0.4.5 catalogue completeness and consolidated TV view

- Watchmode complete refresh now follows the API's authoritative `total_pages` / `total_results` for every selected service rather than stopping at an arbitrary Standard/Deep page count.
- Quick sample remains available and intentionally fetches at most 500 results per service.
- Movie scope includes short films; TV scope includes series, miniseries, specials and TV movies.
- Refresh status reports publisher results, pages fetched, inserted availability rows and local row count separately.
- TV search consolidates service, availability and classification controls into a compact filter panel; service selectors use dense chips rather than a tall checkbox list.

## v0.4.4 selective Watchmode enrichment

- Bulk Watchmode catalogue sync remains the cheap first layer.
- Per-title Watchmode details are fetched only when a title becomes materially interesting (selected/opened, favourited, or added to the watchlist).
- Genres, language, runtime and creator/director metadata are cached locally for 30 days and reapplied after later catalogue refreshes.
- Failed enrichment attempts are held for 12 hours before retry to avoid burning API calls during outages.
- MethodMesh tracks its own Watchmode calls for the current month and shows an approximate remaining allowance against the 2,500-call free developer tier.
- Search facets automatically improve as selective enrichment fills the local catalogue.
- Streaming surfaces retain the required “Streaming data powered by Watchmode.com” attribution.

## v0.4.3 data-driven catalogue facets

Catalogue filters are now derived from the local database rather than hard-coded lists. Provider, media type, genre, language and access-class facets reflect only values actually present in the downloaded catalogue. Genre strings are split into distinct selectable values. Empty classifications are not presented as fake choices; the UI reports when the current downloaded publisher data does not contain that metadata.

## v0.4.2 catalogue/search hardening

- Watchmode refresh depth now downloads substantially more than one 250-title page per service: Quick up to 500 titles/service, Standard up to 2,000, Deep up to 5,000, stopping when the publisher returns a short page.
- Search provider tickboxes now come from the provider names actually stored in the local catalogue, avoiding friendly-name/API-name mismatches.
- Local text search is tokenised and normalised. If direct matching returns nothing, MethodMesh performs a bounded fuzzy fallback over locally filtered candidates, including minor typo tolerance.
- Search remains fully local after catalogue refresh; no remote query is required to find a downloaded title.

## No-sign-in rule

The Media module **does not authenticate the user to Spotify, Netflix, Prime Video, Disney+, BBC, Apple Music or any other consumer media account**.

- MethodMesh owns favourites/watchlist/watched state locally.
- Provider names and availability are catalogue data, not account sessions.
- `media.audio.identify` listens to the physical audio environment and therefore works for a radio, television, venue speakers, somebody else's phone, a streaming app, or other audible source.
- No provider watchlist/My List sync is attempted.

## Audio discovery

`media.audio.identify` records a short temporary microphone clip (default 10 s; configurable 5–20 s) and sends only that clip to an audio-identification service. The current default adapter is **AudD standard recognition**, preset to AudD's documented public `test` token when no private token is configured.

The AudD adapter uses `https://api.audd.io/` multipart recognition. It does not use Spotify OAuth or a user media identity. AudD accepts the documented public `test` token for a small free allowance (currently 10 requests/day). MethodMesh uses that automatically by default. A private AudD token is therefore optional and is treated as an **operational MethodMesh service credential**, not a user sign-in:

- entered once in the audio capability's service setup;
- stored only in module-private Android `SharedPreferences`;
- masked during entry;
- never sent to `context.onSettingsChanged`;
- never written into Presets, Protocols, ODK calls, result fields, logs or exported definitions;
- may be cleared locally at any time.

A missing token produces an explicit configuration error rather than a login flow.

The temporary audio clip is deleted after the recognition attempt. No local audio archive is created by identification.

Successful core fields include:

- `media_title`
- `media_creator` (artist)
- `media_album`
- `media_release_date`
- `media_song_link`
- `media_discovery_provider`
- `media_match_status`
- `media_sample_seconds`
- `media_network_used=true`
- `media_work_id`

The module does **not** invent a confidence score where the recognition service has not returned one.

## TV catalogue and entitlement

Streaming availability is represented separately from a work. A single `work_id` may therefore have several provider rows.

`access_type` is normalised to:

- `subscription_included`
- `free`
- `free_with_ads`
- `rent`
- `buy`
- `addon_subscription`
- `unavailable`
- `unknown`

The catalogue search defaults are deliberately:

- **Included with subscription only = on**
- **Exclude add-on channels = on**
- **Allow free with ads = off**

so a search for a service does not silently mix included titles with rent/buy/add-on offers.

## Favourites, watchlist and watched

These states belong to MethodMesh and never require a provider login.

Each saved work can independently be:

- favourite;
- on the watchlist;
- watched.

They are not mutually exclusive. A watched film may remain a favourite; a favourite may also be on a rewatch list.

`media.library.list` can intersect these local states with current availability. Examples:

- all favourites;
- Netflix favourites;
- Prime Video watchlist;
- watched titles;
- favourites currently **included without extra payment** on a named service.

Because catalogue rows are volatile, a favourite remains a favourite even when it leaves a service. Availability changes never erase personal state.

## Catalogue import

CSV and JSON are supported. Canonical fields are documented in `CATALOGUE_FORMAT.md`. `replace_provider_region` replaces only provider/region pairs represented by the import, and only when both provider and region are non-blank; this prevents an underspecified import from deleting unrelated catalogue rows.

Imported data remains local and usable offline after import.

## Capture and provenance

`media.library.capture` supports books, films, series, music, albums, podcasts and other works. `media_observed_text` remains distinct from resolved title/creator metadata. This is important for book-cover/poster OCR composition: the OCR evidence is not silently rewritten into a resolved catalogue assertion.

The module declares `mlkitvision` only as an optional composition dependency. It does not duplicate the shared OCR stack.

## ODK

`docs/example_odk_showcase_media.xlsx` exercises the canonical public methods. Audio identification is represented as an interactive call:

```text
ODK -> media.audio.identify -> microphone sample -> recognition service -> Commit/return -> ODK
```

The service credential is never supplied by or returned to the XLSForm.

## Permissions / network

- Catalogue, personal library and manual capture: no network permission required at execution time.
- Audio identification: `RECORD_AUDIO` + Internet. These permissions already exist in the host MethodMesh manifest.
- Audio recognition explicitly reports network use in the result.

## Persistence

Module-private SQLite stores:

- imported catalogue rows;
- MethodMesh personal media state.

The v0.2 schema migrates legacy v0.1 personal records best-effort into the new `personal_state` table. Operational audio credentials are kept outside SQLite in private preferences and are deliberately excluded from exportable capability configuration.

## Known limitations

1. The module can refresh TV/film catalogue data from TMDB/JustWatch or Watchmode when the operator supplies the relevant developer API credential; manual CSV/JSON import remains available as a fallback.
2. Streaming availability quality is only as current as the imported source and `last_verified` value.
3. Audio discovery requires network access and an API token for the configured recognition provider.
4. Recognition success depends on source volume, noise, sample quality and whether the provider recognises the recording.
5. Provider account watchlists/My List are intentionally not synced.
6. Direct camera OCR remains composition with `mlkitvision`, not duplicate Media code.


## Native visual language

v0.3.0 treats native capability screens as finished media surfaces rather than generic settings/results pages. Search and library actions stay attached to the selected media item; capture and state editing transition into a committed title state; audio identification centres the listening action and identified track. Large generic result boxes and raw-result dumps are intentionally avoided.


## v0.4.0 catalogue publishers and TV dashboard

The native TV/film search surface now acts as a lightweight dashboard: local catalogue size, favourites and watchlist are visible above the search workflow; services are selected through a searchable multi-select rather than comma-separated text. The library uses searchable provider selection too.

`Catalogue Sources` is publisher-first. TMDB/JustWatch is the default baseline; Watchmode is optional. Both credentials are private module service credentials and are never projected into presets, protocols, ODK or method results. Region, services, media classes and refresh depth are explicit native controls. Manual CSV/JSON import is retained under Advanced.

TMDB watch-provider data is powered by JustWatch and requires attribution. The module surfaces: `TMDB availability is powered by JustWatch. This product uses the TMDB API but is not endorsed or certified by TMDB.` Host-app About/Credits should preserve the required attribution.

## Publisher attribution and API-key links

When Watchmode data is shown, the native UI displays the clickable attribution **Streaming data powered by Watchmode.com** linking to https://www.watchmode.com/. The free Watchmode developer plan requires attribution and cached Watchmode data should be refreshed or removed within 30 days.

The Catalogue Sources screen includes direct links to:

- Watchmode free API key: https://api.watchmode.com/requestApiKey
- Watchmode API documentation: https://api.watchmode.com/docs
- TMDB API setup: https://developer.themoviedb.org/docs/getting-started
- AudD API/token documentation: https://docs.audd.io/

API credentials remain private operational configuration and are not emitted through ODK, presets, protocols, or method results.

