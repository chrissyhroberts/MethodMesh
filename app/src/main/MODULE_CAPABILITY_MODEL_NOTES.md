# Module capability model notes

This build moves the runtime towards self-contained capability modules.

## Design rule

A capability should be added by creating a folder under `modules/` and placing all capability-specific implementation there. A module owns its own:

- legacy `Method` shell, where still required
- canonical AS/ResearchOS method implementation
- focused external workflow screen
- RIL phrase bindings
- graph/output mapping helpers
- device-service boundary code specific to the capability

The central runtime should not need method-specific `when` branches when a new capability is added.

## Module contract

Each module exposes one object implementing `ResearchOSModule`:

```kotlin
object ExampleModule : ResearchOSModule {
    override val moduleId = "example"
    override val displayName = "Example"

    override fun legacyMethods() = listOf(ExampleMethod())
    override fun as100Methods() = listOf(As100ExampleMethod)
    override fun rilBindings() = listOf(
        RilBinding("capture example", "example.capture")
    )
    override fun capabilityScreens() = listOf(ExampleCapabilityScreen)
}
```

## Runtime discovery

`ResearchOSModuleRegistry` discovers module objects from the Android dex under `com.example.researchos.modules.*Module`. It also includes a fallback list for the existing built-in modules so the app remains usable in non-Android/editor contexts.

The current built-in modules are:

- `modules/nfc`
- `modules/adminfingerprint`
- `modules/gpstargetnavigator`
- `modules/calibratedscale`

## Current limitation

Android runtime discovery depends on the module object class name ending in `Module`. For example:

```text
modules/mycapability/MyCapabilityModule.kt
```

The fallback list still references the current built-in modules. New modules should be discovered at runtime without central edits, but if a non-Android JVM-only context needs to see them, an explicit test bootstrap may still be required.
