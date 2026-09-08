# Time Tools roadmap

Development admission priorities:

- Bind `TimeToolsModuleDefinition` to the repository's current `MethodMeshModule`/descriptor/`MethodSetting` API.
- Add repository-standard result/closeout adapters for all six capabilities.
- Add foreground-service and notification integration for active countdown/interval operations.
- Persist authoritative active-timer state using the repository-standard state mechanism.
- Add polished module-owned configuration screens for interval programme editing and native target-time selection.
- Wire copy/share/save actions to generic MethodMesh result actions.
- Validate widget-launched Done -> Android desktop and app-launched Done -> MethodMesh dashboard.
- Align `example_odk_time_tools.xlsx` intent declaration syntax to a current known-good MethodMesh XLSForm before Production promotion.
- Add focused deterministic tests using a fake `MonotonicClock`.

Do not solve any of these by adding Time Tools-specific logic to shared MethodMesh UI.
