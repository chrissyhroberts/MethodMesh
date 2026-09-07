# Astronomy v0.3.1 changes

Compile-fix release for v0.3.0.

- Added the missing `AstronomyMath.MoonHorizontal` result type.
- Added `AstronomyMath.moonHorizontal(instant, latitudeDeg, longitudeDeg)` to return lunar altitude, azimuth and illumination for `astronomy.conditions`.
- Made dew-risk temperature and relative-humidity resolution explicitly non-null before calling `AstronomyMath.dewRisk`, avoiding nullable `Double?` inference at the call site.
- Verified `AstronomyMath.kt` compiles directly with `kotlinc`.
- Verified `AstronomyMethod.kt` + `AstronomyModule.kt` compile against stubs matching the current MethodMesh public contracts retrieved from the repository.

No public method IDs or output field names changed.
