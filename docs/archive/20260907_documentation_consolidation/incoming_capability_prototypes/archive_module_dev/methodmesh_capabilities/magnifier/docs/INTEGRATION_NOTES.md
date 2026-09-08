# Integration notes

- Canonical destination: `app/src/main/java/com/example/methodmesh/modules/magnifier/`.
- Lifecycle: Development. Add a matching Development entry/known limitations note to `000_Roadmap.md` when merging.
- Uses the generic immersive host contract from `METHODMESH_FULLSCREEN_CAPABILITY_GUIDE.md` and the app's existing CameraX/FileProvider boundary; no magnifier-specific host branch is required.
- Camera permission denial and missing/unavailable camera controls are handled in the capability UI.
- After copying into the target tree, run `./gradlew :app:assembleDebug` plus physical-device camera testing before promotion.
