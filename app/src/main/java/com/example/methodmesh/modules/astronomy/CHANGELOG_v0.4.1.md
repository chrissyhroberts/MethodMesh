# Astronomy v0.4.1 changes

Compile-fix release for the v0.4 dashboard integration.

- Removed the obsolete file-local `hasLocationPermission(Context)` from `AstronomyCapabilityScreen.kt`; v0.4 had exposed the shared helper from `AstronomyAutoScreens.kt`, making the two same-signature functions ambiguous inside the older screen file.
- This resolves the reported `Overload resolution ambiguity`, `Conflicting overloads`, and cascading `Unresolved reference not` diagnostics.
- Added explicit callback parameter types to the location and camera permission launchers to avoid generic inference failures in `rememberLauncherForActivityResult`.
- No capability IDs, field names, calculation semantics, dashboard layout, or ODK return names changed.
