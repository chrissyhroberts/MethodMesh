# Weather v0.2.5 — preset launch hardening

## Fixed

- Removed nested root vertical scrolling during preset, ODK, protocol and other intent-launched Weather execution. `ExternalWorkflowActivity` already owns that scroll container; Weather keeps its own root scroller only for the direct immersive Dashboard presentation.
- Aligned native-preset `HOME` completion with the shared `CapabilityScreenScaffold` closeout behaviour so finishing a Weather preset explicitly returns to MethodMesh instead of falling through the transient dispatcher/task.
- Preserved the same-screen Commit/result actions introduced in v0.2.4.

## Contracts

- No method IDs changed.
- No canonical inputs or outputs changed.
- No XLSForms or ODK intents changed.
- Preset fixed/runtime field semantics are unchanged.

## Validation

- Current MethodMesh host scrolling and native-preset closeout implementations were compared before patching.
- Kotlin parser scan: no syntax errors.
- Eleven XLSForms remain byte-identical to v0.2.4.
- Full receiving-tree Gradle/device validation remains required.
