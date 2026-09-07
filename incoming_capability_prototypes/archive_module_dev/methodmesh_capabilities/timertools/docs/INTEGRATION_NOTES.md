# Integration notes

- Canonical destination: `app/src/main/java/com/example/methodmesh/modules/timertools/`.
- Lifecycle: Development. Add a matching Development entry/known limitations note to `000_Roadmap.md` when merging.
- Uses the generic immersive host contract from `METHODMESH_FULLSCREEN_CAPABILITY_GUIDE.md`; no timer-specific host branch is required.
- No alarm, notification or foreground-service dependency is introduced.
- After copying into the target tree, run `./gradlew :app:assembleDebug` and focused tests before promotion.
