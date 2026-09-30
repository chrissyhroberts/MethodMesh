# MethodMesh Weather v0.5.1

## Compile fixes

- Removed the explicit `androidx.compose.foundation.layout.weight` import from `WeatherCapabilityScreens.kt`; `Modifier.weight(...)` now resolves through the public `RowScope` API.
- Changed the shared `String.toStringMap()` helper from file-private to module-internal so `WeatherDashboardScreen.kt` can use it for frozen committed dashboard/settings state.

No method IDs, inputs, outputs, ODK fields or XLSForm contracts changed from v0.5.0.
