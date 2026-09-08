# Astronomy v0.4.2 changes

Compile-fix release.

- Restores the three pure-calculation capability screen declarations that were present in v0.1.0 but were accidentally dropped during the v0.2 auto-data screen refactor:
  - `ImageScaleCapabilityScreen`
  - `ExposureLimitCapabilityScreen`
  - `SessionCapabilityScreen`
- Keeps the existing module registrations and stable method IDs unchanged.
- Verifies every `AstronomyModule.capabilityScreens()` registration has exactly one matching declaration.
- Verifies every `AstronomyModule.as100Methods()` registration has exactly one matching method object.
