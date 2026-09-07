# Integration notes

- Canonical destination: `app/src/main/java/com/example/methodmesh/modules/datatools/`.
- Lifecycle: Development. Add a matching Development entry/known limitations note to `000_Roadmap.md` when merging.
- Encoding/parsing logic is pure Kotlin/JVM/`org.json` and intended for focused unit tests in the repository test source set.
- YAML/XML dependencies are deliberately not introduced.
- After copying into the target tree, run `./gradlew :app:assembleDebug` and focused tests before promotion.
