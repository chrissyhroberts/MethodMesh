# Media implementation plan — after v0.4.0

## Completed in v0.2

- MethodMesh-owned favourites / watchlist / watched state.
- Service-filtered favourites and watchlist shelves.
- Included-without-extra-payment intersection against current catalogue.
- Canonical `media.library.list` and `media.library.state` methods.
- Ambient `media.audio.identify` capability using a no-user-sign-in recognition adapter.
- AudD multipart adapter with private local operational token storage.
- Temporary microphone sample lifecycle and deletion after recognition.
- Audio-result to local Favourite action.
- ODK surface expansion for new methods.

## Next candidates

1. **Media home / capture tray** — richer module dashboard composed from the same canonical methods: Listen, Scan, Search, Favourites, Watchlist, Library.
2. **OCR composition** — launch/pipeline `mlkitvision` OCR into `media.library.capture`, preserving observed text separately from resolved work.
3. **Metadata resolution** — optional work resolvers for books (ISBN/Open Library) and film/TV metadata; no user account required.
4. **Catalogue adapters** — DONE in v0.4.0: TMDB/JustWatch baseline plus optional Watchmode publisher refresh; manual import retained.
5. **Availability history** — retain enough snapshots to expose Added, Leaving/Removed, and service moves.
6. **Random picker** — constrained Surprise Me over local catalogue/personal state.
7. **Credential service migration** — if MethodMesh gains a shared secure-credential registry, migrate the AudD token from module-private preferences to that shared service without changing the public capability contract.

- v0.4.0: searchable service multi-select, TV dashboard summary, publisher-first catalogue refresh, and free-default AudD recognition.


## Selective enrichment (v0.4.4)

Watchmode bulk catalogue rows are intentionally lightweight. Full title details are fetched on demand for selected, favourited and watchlisted titles, cached for 30 days, and reapplied after catalogue refreshes. This keeps the local catalogue large while conserving the free-plan request allowance.


## Catalogue completeness (v0.4.5)

Watchmode bulk refresh must use the response `total_pages` as the completion boundary. Fixed page-depth presets are not a reliable definition of a complete service catalogue. The default native refresh is Complete; Quick sample is explicitly partial.
