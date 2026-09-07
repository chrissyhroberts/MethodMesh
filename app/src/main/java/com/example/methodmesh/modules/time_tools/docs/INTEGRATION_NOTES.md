# Integration notes

This handoff was created from the MethodMesh Master Book without the live MethodMesh source repository. Therefore:

- timing logic is concrete;
- method IDs and output contracts are concrete;
- Compose capability surfaces are concrete starting points;
- exact repository binding names in `TimeToolsModule.kt` are intentionally isolated rather than fabricated;
- foreground-service wiring must use the current app's service/notification conventions;
- XLSForm external-intent syntax must be copied from a current known-good MethodMesh example during admission.

The folder should be admitted through normal Work-mode review. If exact current interfaces differ, adapt the adapter layer rather than changing the capability contract or moving logic into the shared UI.

## 2026-09-06 dependency-hardening revision

The handoff no longer imports Jetpack Compose, `androidx.lifecycle.viewmodel.compose`, coroutines,
JUnit, or `org.json`. The files named `*CapabilityScreen.kt` now expose repository-neutral
presentation/controller contracts. Bind those to the current MethodMesh native screen mechanism
when admitting the module. This avoids forcing a UI or test dependency into the host app.

`TimerEngineSelfTest.runAll()` provides zero-dependency engine contract checks. The repository may
mirror those cases in its existing unit-test framework after integration.
