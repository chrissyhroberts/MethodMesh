# Integration notes

- Canonical destination: `app/src/main/java/com/example/methodmesh/modules/networktools/`.
- Lifecycle: Development. Add a matching Development entry/known limitations note to `000_Roadmap.md` when merging.
- No broad LAN/port scanning is present. CIDR logic is pure and should receive focused unit tests in the repository test source set.
- The current MethodMesh permissions cover the main network operations. Some Android builds may expose fuller Wi-Fi details when the app also declares `ACCESS_WIFI_STATE`; the capability degrades to an explicit unavailable result if details are restricted.
- After copying into the target tree, run `./gradlew :app:assembleDebug` and focused tests before promotion.
