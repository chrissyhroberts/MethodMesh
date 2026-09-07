# Integration notes

- Canonical destination: `app/src/main/java/com/example/methodmesh/modules/conversions/`.
- Lifecycle: Development. Add a matching Development entry/known limitations note to `000_Roadmap.md` when merging.
- Calculation logic is pure Kotlin and intended for focused unit tests in the repository test source set.
- No dependencies, permissions or resources outside the module are required.
- After copying into the target tree, run `./gradlew :app:assembleDebug` and focused tests before promotion.
