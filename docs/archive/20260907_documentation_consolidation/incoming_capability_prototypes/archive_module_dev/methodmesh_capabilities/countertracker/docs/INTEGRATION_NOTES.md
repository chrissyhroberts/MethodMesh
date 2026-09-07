# Integration notes

- Canonical destination: `app/src/main/java/com/example/methodmesh/modules/countertracker/`.
- Lifecycle: Development. Add a matching Development entry/known limitations note to `000_Roadmap.md` when merging.
- Uses the generic immersive host contract from `METHODMESH_FULLSCREEN_CAPABILITY_GUIDE.md`; no counter-specific host branch is required.
- Pure snapshot/state logic is separated from UI in `CounterTrackerMethod.kt` and `CounterTrackerRepository.kt`.
- After copying into the target tree, run `./gradlew :app:assembleDebug` and focused tests before promotion.
