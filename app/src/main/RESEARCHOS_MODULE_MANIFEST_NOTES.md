# ResearchOS module manifest hardening

This patch replaces opportunistic Dex scanning plus a hidden fallback module list
with an explicit `ResearchOSModuleManifest`.

## Changed files

- `java/com/example/researchos/modules/ResearchOSModule.kt`
  - Removed Dex scanning and fallback merge behaviour.
  - Kept `ResearchOSModuleRegistry.initialise(context)` as a no-op compatibility method for existing Android call sites.
  - `ResearchOSModuleRegistry.all()` now reads from `ResearchOSModuleManifest.modules`.

- `java/com/example/researchos/modules/ResearchOSModuleManifest.kt`
  - New explicit list of built-in modules.
  - This is now the only central registration file required when adding or removing a built-in capability.

## Adding a capability

1. Add a folder under `java/com/example/researchos/modules/<capability>/`.
2. Define one object implementing `ResearchOSModule`.
3. Keep AS methods, legacy method adapters, RIL bindings, screens, examples and result helpers inside that module folder.
4. Add that module object to `ResearchOSModuleManifest.modules`.

The Capabilities panel, RIL parser, external workflow router and AS method registry
all consume the same manifest-backed registry.
