# Aviation v0.3 compile fix

Fixes applied to `AviationEmergencyInstrumentsCapabilityScreen.kt` after `:app:compileDebugKotlin` errors:

1. Removed `import androidx.compose.foundation.layout.weight`.
   - `Modifier.weight(...)` is a scoped `RowScope`/`ColumnScope` extension in this Compose version.
   - Explicitly importing the internal implementation caused `Cannot access 'val RowColumnParentData?.weight: Float': it is internal in file`.

2. Replaced the nonexistent `AviationCalculations.distanceNm(...)` call with:
   - `AviationCalculations.distanceAndBearing(...).distanceNm`

3. Replaced fully-qualified `androidx.compose.material3.ExposedDropdownMenu(...)` with the receiver-scoped `ExposedDropdownMenu(...)` call inside `ExposedDropdownMenuBox`.
   - This matches the already-used pattern in `AviationDashboardCapabilityScreen.kt` and `AviationCapabilityScreens.kt`.

4. The reported `@Composable invocations can only happen from the context of a @Composable function` error was treated as a cascading scope-resolution error from items 1 and 3. The affected calls remain inside valid `Row`, `Column`, and `ExposedDropdownMenuBox` receiver scopes.

## Validation limitation

A full Gradle compile could not be run in the current execution container because outbound DNS/network access is disabled, preventing a clean MethodMesh checkout. The corrected source was instead checked against the existing v0.2 Compose patterns already present in the patch.
