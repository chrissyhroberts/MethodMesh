# Runtime fix — v0.1.2

The v0.1.1 capability compiled but could crash immediately when opened from the MethodMesh dashboard.

Cause: the capability rendered `Column(...verticalScroll(...))` inside MethodMesh surfaces that are themselves hosted by a top-level `LazyColumn`. Compose rejects vertically scrollable children measured with effectively unbounded vertical constraints.

Fix:
- removed `verticalScroll` from the game library;
- removed `verticalScroll` from the workspace dashboard;
- removed the now-unused `rememberScrollState` / `verticalScroll` imports;
- kept scrolling ownership with the MethodMesh host surface.

No shared framework or dashboard changes are required.
