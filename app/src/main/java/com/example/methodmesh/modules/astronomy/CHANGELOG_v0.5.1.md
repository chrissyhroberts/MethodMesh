# Astronomy v0.5.1

Light-pollution cache UX and integration repair.

- Treats MethodMesh `intent_test` launches as native interactive use rather than external auto-return.
- A cache miss now stays on the light-pollution management screen so a region can be imported.
- Genuine external/ODK cache misses return a non-empty main result plus failure/error metadata.
- Adds **Check this site** and an explicit **Use this result** / **Finish with this result** flow.
- Shows stored regions and identifies the region covering the selected site.
- Adds light-pollution region import directly to the astronomy dashboard Site darkness card.
- Dashboard and standalone light-pollution capability use the same module-owned repository and refresh after import.
- Does not modify HomeScreen, Settings, widget models or any other MethodMesh core UI.
